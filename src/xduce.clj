(ns xduce
  (:refer-clojure :exclude [interleave eduction sequence frequencies])
  (:require [xduce.educe :as educe]
            [clojure.core.reducers :as r])
  (:import [xduce.educe Educe]
           java.util.concurrent.atomic.AtomicInteger
           java.util.concurrent.ConcurrentHashMap
           [java.util HashMap Collections Map]))

(set! *warn-on-reflection* true)
(def ^:const ncpu (.availableProcessors (Runtime/getRuntime)))

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
    * It's not lazy. The input is coerced into a vector.
    * It does not support nil values. Theu are removed automatically.
    * Only stateless transducers are allowed in xforms."
  [coll & xforms]
  (let [coll (into [] coll)
        m (ConcurrentHashMap. (quot (count coll) 2) 0.75 ncpu)
        combinef (fn ([] m) ([_ _]))
        rf (fn [^Map m k]
             (let [^AtomicInteger v (or (.get m k) (.putIfAbsent m k (AtomicInteger. 1)))]
               (when v (.incrementAndGet v))
               m))
        reducef ((apply comp (conj xforms (remove nil?))) rf)]
    (r/fold combinef reducef coll)
    (into {} m)))
