(ns parallel
  (:refer-clojure :exclude [interleave eduction sequence frequencies])
  (:require [parallel.educe :as educe]
            [clojure.core.reducers :as r])
  (:import [parallel.educe Educe]
           [java.util.concurrent.atomic AtomicInteger]
           java.util.concurrent.ConcurrentHashMap
           [java.util HashMap Collections Map]))

(set! *warn-on-reflection* true)
(def ^:const ncpu (.availableProcessors (Runtime/getRuntime)))

(def ^:dynamic *mutable* false)

(defn eduction
  [& xforms]
  (Educe. (apply comp (butlast xforms)) (last xforms)))

(defn sequence
  ([xform coll]
     (or (clojure.lang.RT/chunkIteratorSeq
         (educe/create xform (clojure.lang.RT/iter coll)))
       ()))
  ([xform coll & colls]
     (or (clojure.lang.RT/chunkIteratorSeq
         (educe/create
           xform
           (map #(clojure.lang.RT/iter %) (cons coll colls))))
       ())))

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

(defn frequencies
  "Like clojure.core/frequencies, but executes in parallel.
  It takes an optional list of transducers to apply to coll before
  the frequency is calculated. Restrictions:
    * It does not support nil values.
    * Only stateless transducers are allowed in xforms."
  [coll & xforms]
  (let [coll (into [] coll)
        m (ConcurrentHashMap. (quot (count coll) 2) 0.75 ncpu)
        combinef (fn ([] m) ([_ _]))
        rf (fn [^Map m k]
             (let [^AtomicInteger v (or (.get m k) (.putIfAbsent m k (AtomicInteger. 1)))]
               (when v (.incrementAndGet v))
               m))
        reducef (if (seq xforms) ((apply comp xforms) rf) rf)]
    (r/fold combinef reducef coll)
    (if *mutable* m (into {} m))))

(defn update-vals
  "Use f to update the values of a map in parallel. It performs well
  with non-trivial f, otherwise is outperformed by reduce-kv.
  For larger maps (> 100k keys), the final transformation
  from mutable to persistent dominates over trivial f trasforms.
  You can access the raw mutable result setting the dynamic
  binding *mutable* to true. Restrictions:
    * Does not support nil values."
  [^Map input f]
  (let [ks (into [] (keys input))
        output (ConcurrentHashMap. (count ks) 1. ncpu)]
    (r/fold
      (fn ([] output) ([_ _]))
      (fn [^Map m k]
        (.put m k (f (.get input k)))
        m)
      ks)
    (if *mutable* output (into {} output))))
