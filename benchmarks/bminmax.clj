(ns bminmax)

(require '[criterium.core :refer [quick-bench]])
(require '[parallel :as p] :reload)
; (require '[clojure.core.reducers :as r] :reload)
(def coll  (into [] (conj (shuffle (range 1000000)) -9)))
(def coll2 (into [] (conj (shuffle (range 5000000)) -9)))

(let [c coll]  (quick-bench (reduce min (peek c) c))) ;; 14 ms
(let [c coll2] (quick-bench (reduce min (peek c) c))) ;; 160 ms

(let [c coll]  (quick-bench (p/min c))) ;; 5ms
(let [c coll2] (quick-bench (p/min c))) ;; 40ms
(let [c coll]  (quick-bench (p/min c (map inc)))) ;; 15ms

;; experiments...
(let [c coll2]
  (quick-bench
    (r/fold
      8000
      min
      (fn [v] (nth (sort v) 0))
      (reify r/CollFold
        (coll-fold [this n combinef f]
          (p/foldvec c n combinef f))))))
;; 647ms
