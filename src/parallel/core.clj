(ns parallel.core
  (:refer-clojure :exclude [interleave eduction sequence frequencies
                            count group-by sort min max amap])
  (:require [parallel.foldmap :as fmap]
            [parallel.merge-sort :as msort]
            [parallel.map-combine :as mcombine]
            [clojure.core.reducers :as r]
            [clojure.core.protocols :as p]
            [clojure.java.io :as io])
  (:import
    [parallel.merge_sort MergeSort]
    [parallel.map_combine MapCombine]
    [java.io File]
    [java.util.concurrent.atomic AtomicInteger AtomicLong]
    [java.util.concurrent ConcurrentHashMap ConcurrentLinkedQueue]
    [java.util HashMap Collections Queue Map]))

(set! *warn-on-reflection* true)
(def ^:const ncpu (.availableProcessors (Runtime/getRuntime)))

(def ^:dynamic *mutable* false)

(defn interleave
  [coll]
  (fn [rf]
    (let [fillers (volatile! (seq coll))]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (if-let [[filler] @fillers]
           (let [step (rf result input)]
             (if (reduced? step)
               step
               (do
                 (vswap! fillers next)
                 (rf step filler))))
           (reduced result)))))))

(defn- foldable? [coll]
  (or (map? coll)
      (vector? coll)
      (instance? clojure.core.reducers.Cat coll)))

(defn- compose
  "As a consequence, reducef cannot be a vector."
  [xrf]
  (if (vector? xrf)
    ((peek xrf) (nth xrf 0))
    xrf))

(defn xrf
  "Expects a reducing function rf and a list
  of transducers (or comp thereof). Use with
  p/fold to compose any chain of transducers applied to
  a reducing function to run in parallel."
  [rf & xforms]
  (if (empty? xforms)
    rf
    [rf (apply comp xforms)]))

(defn- splitting
  "Calculates split sizes as they would be generated by
  a parallel fold with n=1."
  [coll]
  (iterate
    #(mapcat
       (fn [n] [(quot n 2) (- n (quot n 2))]) %)
    [(clojure.core/count coll)]))

(defn show-chunks
  "Shows chunk sizes for the desired chunk number
  on a given collection coll."
  [coll nchunks]
  {:pre [(== (bit-and nchunks (- nchunks)) nchunks)]}
  (->> (splitting coll)
       (take-while #(<= (clojure.core/count %) nchunks))
       last))

(defn chunk-size
  "Calculates the necessary chunk-size to obtain
  the given number of splits during a parallel fold.
  nchunks needs to be a power of two."
  [coll nchunks]
  (apply clojure.core/max (show-chunks coll nchunks)))

(defn foldvec
  "A general purpose reducers/foldvec taking a generic f
  to apply at the leaf instead of reduce."
  [v n combinef f]
  (let [cnt (clojure.core/count v)]
    (cond
      (empty? v) (combinef)
      (<= cnt n) (f v)
      :else (let [half (quot cnt 2)
                  r1 (subvec v 0 half)
                  r2 (subvec v half cnt)
                  fc (fn [v] #(foldvec v n combinef f))]
              (#'r/fjinvoke
                #(let [f1 (fc r1)
                       t2 (#'r/fjtask (fc r2))]
                   (#'r/fjfork t2)
                   (combinef (f1) (#'r/fjjoin t2))))))))

(defprotocol Folder
  (folder [coll]
          [coll nchunks]))

(extend-protocol Folder
  Object
  (folder
    ([coll]
     (reify r/CollFold
       (coll-fold [this n combinef reducef]
         (r/reduce reducef (combinef) coll))))
    ([coll nchunks]
     (reify r/CollFold
       (coll-fold [this _ combinef reducef]
         (r/reduce reducef (combinef) coll)))))
  clojure.lang.IPersistentVector
  (folder
    ([coll]
     (reify r/CollFold
       (coll-fold [this n combinef reducef]
         (foldvec coll n combinef #(r/reduce (compose reducef) (combinef) %)))))
    ([coll nchunks]
     (reify r/CollFold
       (coll-fold [this _ combinef reducef]
         (foldvec coll (chunk-size coll nchunks) combinef #(r/reduce (compose reducef) (combinef) %))))))
  clojure.lang.PersistentHashMap
  (folder
    ([coll]
     (reify r/CollFold
       (coll-fold [m n combinef reducef]
         (fmap/fold coll 512 combinef reducef))))
    ([coll nchunks]
     (reify r/CollFold
       (coll-fold [m n combinef reducef]
         (fmap/fold coll 512 combinef reducef))))))

(defn fold
  "Like reducers fold, but with stateful transducers support.
  Expect reducef to be built using p/xrf to defer initialization.
  n is the number-of-chunks instead of chunk size.
  n must be a power of 2 and defaults to 32."
  ([reducef coll]
   (fold (first reducef) reducef coll))
  ([combinef reducef coll]
   (fold 32 combinef reducef coll))
  ([n combinef reducef coll]
   (r/fold ::ignored combinef reducef (folder coll n))))

(defn count
  ([xform coll]
   (count 32 xform coll))
  ([n xform coll]
   (let [coll (if (foldable? coll) coll (into [] coll))
         cnt (AtomicLong. 0)
         reducef (xrf (fn [_ _] (.incrementAndGet cnt)) xform)
         combinef (constantly cnt)]
     (fold n combinef reducef coll)
     (.get cnt))))

(extend-protocol clojure.core.protocols/IKVReduce
  java.util.Map
  (kv-reduce
    [amap f init]
    (let [^java.util.Iterator iter (.. amap entrySet iterator)]
      (loop [ret init]
        (if (.hasNext iter)
          (let [^java.util.Map$Entry kv (.next iter)
                ret (f ret (.getKey kv) (.getValue kv))]
            (if (reduced? ret)
              @ret
              (recur ret)))
          ret)))))

(defn group-by
  "Similar to core/group-by, but executes in parallel.
  It takes an optional list of transducers to apply to the
  items in coll before generating the groups. Differently
  from core/group-by, the order of the items in each
  value vector can change between runs. It's generally 2x-5x faster
  than core/group-by (without xducers). If dealing with a Java mutable
  map with Queue type values is not a problem, a further 2x
  speedup can be achieved by:
        (binding [p/*mutable* true] (p/group-by f coll))
  Restrictions: it does not support nil values."
  [f coll & xforms]
  (let [coll (if (foldable? coll) coll (into [] coll))
        m (ConcurrentHashMap. (quot (clojure.core/count coll) 2) 0.75 ncpu)
        combinef (fn ([] m) ([m1 m2]))
        rf (fn [^Map m x]
             (let [k (f x)
                   ^Queue a (or (.get m k) (.putIfAbsent m k (ConcurrentLinkedQueue. [x])))]
               (when a (.add a x))
               m))]
    (fold combinef (apply xrf rf xforms) coll)
    (if *mutable* m (persistent! (reduce-kv (fn [m k v] (assoc! m k (vec v))) (transient {}) m)))))

(defn frequencies
  "Like clojure.core/frequencies, but executes in parallel.
  It takes an optional list of transducers to apply to coll before
  the frequency is calculated. It does not support nil values."
  [coll & xforms]
  (let [coll (if (foldable? coll) coll (into [] coll))
        m (ConcurrentHashMap. (quot (clojure.core/count coll) 2) 0.75 ncpu)
        combinef (fn ([] m) ([_ _]))
        rf (fn [^Map m k]
             (let [^AtomicInteger v (or (.get m k) (.putIfAbsent m k (AtomicInteger. 1)))]
               (when v (.incrementAndGet v))
               m))]
    (fold combinef (apply xrf rf xforms) coll)
    (if *mutable* m (into {} m))))

(defn update-vals
  "Use f to update the values of a map in parallel. It performs well
  with non-trivial f, otherwise is outperformed by reduce-kv.
  For larger maps (> 100k keys), the final transformation
  from mutable to persistent dominates over trivial f trasforms.
  You can access the raw mutable java.util.Map by setting the dynamic
  binding *mutable* to true. Restrictions: does not support nil values."
  [^Map input f]
  (let [ks (into [] (keys input))
        output (ConcurrentHashMap. (clojure.core/count ks) 1. ncpu)]
    (r/fold
      (fn ([] output) ([_ _]))
      (fn [^Map m k]
        (.put m k (f (.get input k)))
        m)
      ks)
    (if *mutable* output (into {} output))))

(defn lazy-sort
  "Lazily merge already sorted collections. Maintains order
  through given comparator (or compare by default)."
  ([colls]
   (lazy-sort compare colls))
  ([cmp colls]
   (lazy-seq
     (if (some identity (map first colls))
       (let [[[winner & losers] & others] (sort-by first cmp colls)]
         (cons winner (lazy-sort cmp (if losers (conj others losers) others))))))))

(defn sort
  "Splits input coll into chunk of 'threshold' (default 8192)
  size then sorts chunks in parallel. Input needs converstion into a native
  array before splitting. More effective for large colls
  (> 1M elements) or non trivial comparators. Set *mutable* to 'true'
  to access the raw results as a mutable array."
  ([coll]
   (sort 8192 < coll))
  ([cmp coll]
   (sort 8192 cmp coll))
  ([threshold cmp ^Object coll]
   (let [a (if (.. coll getClass isArray) coll (to-array coll))]
     (msort/sort threshold cmp a)
     (if *mutable* a (into [] a)))))

(defn external-sort
  "Allows large datasets (that would otherwise not fit into memory)
  to be sorted in parallel. Data to fetch is identified by a vector of IDs.
  IDs are split into chunks which are processed in parallel using reducers.
  'fetchf' is used on each ID to retrieve the relevant data.
  The chunk is sorted using 'cmp' ('compare' by default) and saved to disk
  to a temporary file that is deleted when the JVM exits.
  The list of file handles is then used to merge the pre-sorted chunks lazily
  while maintaining order."
  ([fetchf ids]
   (external-sort compare fetchf ids))
  ([cmp fetchf ids]
   (external-sort 512 compare fetchf ids))
  ([n cmp fetchf ids]
   (letfn [(load-chunk [fname] (read-string (slurp fname)))
           (save-chunk! [data]
             (let [file (File/createTempFile "mergesort-" ".tmp")]
               (with-open [fw (io/writer file)] (binding [*out* fw] (pr data) file))))]
     (->>
       (r/fold n concat
         (fn [chunk] (->> chunk (map fetchf) (clojure.core/sort cmp) save-chunk! vector))
         (reify r/CollFold
           (coll-fold [this n combinef f]
             (foldvec (into [] ids) n combinef f))))
       (map load-chunk)
       (lazy-sort cmp)))))

(defn min
  ([v]
   (let [pivot (peek v)]
     (r/fold
       (fn ([] pivot) ([a b] (clojure.core/min a b)))
       clojure.core/min v)))
  ([v & xforms]
   (let [pivot (peek v)]
     (fold
       (fn ([] pivot) ([a b] (clojure.core/min a b)))
       (apply xrf clojure.core/min xforms)
       v))))

(defn max
  ([v]
   (let [pivot (peek v)]
     (r/fold
       (fn ([] pivot) ([a b] (clojure.core/max a b)))
       clojure.core/max v)))
  ([v & xforms]
   (let [pivot (peek v)]
     (fold
       (fn ([] pivot) ([a b] (clojure.core/max a b)))
       (apply xrf clojure.core/max xforms)
       v))))

(defn amap
  "Applies f in parallel to the elements in the array.
  The threshold decides how big a chunk of computation should be before
  going sequential and it's given a default based on the number of
  available cores."
  ([f ^objects a]
   (amap (quot (alength a) (* 2 ncpu)) f a))
  ([threshold f ^objects a]
   (mcombine/map
     (fn [low high ^objects a]
       (loop [idx low]
         (when (< idx high)
           (aset a idx (f (aget a idx)))
           (recur (unchecked-inc idx)))))
     (fn [_ _])
     threshold a)
   a))