(ns parallel.xf
  (:refer-clojure :exclude [interleave]))

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
