(ns binterleave)

(require '[parallel :as p])
(require '[criterium.core :refer [bench quick-bench]])

(let [coll (range 1e5)]
  (quick-bench (doall (interleave (map inc coll) (range)))))
;; 14ms

(let [coll (range 1e5)]
  (quick-bench (doall (sequence (comp (map inc) (p/interleave (range))) coll))))
;; 40ms

(let [coll (range 1e5)]
  (quick-bench (doall (map str (filter odd? (interleave (map inc coll) (range)))))))
;; 37ms

(let [coll (range 1e5)]
  (quick-bench (doall (sequence (comp (map inc) (p/interleave (range)) (filter odd?) (map str)) coll))))
;; 40ms
