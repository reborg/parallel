(ns parallel.xf
  (:refer-clojure :exclude [interleave pmap]))

(defn interleave
  "Transducer version of core/interleave."
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

(defn pmap
  "Like map transducer, but items are processed in chunk of up to 32 items
  in parallel. Only effective with computational intensive f. Unlike normal
  map/pmap, it does not accept multiple inputs."
  [f]
  (comp
    (partition-all 32)
    (fn [rf]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input] (rf result (clojure.core/pmap f input)))))
    cat))
