(ns bminmax)

(require '[criterium.core :refer [bench]])
(require '[parallel.core :as p] :reload)

(def v10k   (conj (shuffle (range 10000)) -9))
(def v100k  (conj (shuffle (range 100000)) -9))
(def v1m    (conj (shuffle (range 1000000)) -9))

;; core reduce
(let [c v10k]   (bench (reduce min c))) ;; 98.237074 µs
(let [c v100k]  (bench (reduce min c))) ;; 1.139608 ms
(let [c v1m]    (bench (reduce min c))) ;; 9.963971 ms

;; core apply (slower than reduce)
(let [c v10k]   (bench (apply min c))) ;; 105.267586 µs
(let [c v1m]    (bench (apply min c))) ;; 8.764973 ms

;; parallel
(let [c v10k]   (bench (p/min c))) ;; 83.043014 µs
(let [c v100k]  (bench (p/min c))) ;; 665.367802 µs
(let [c v1m]    (bench (p/min c))) ;; 5.474384 ms

;; parallel xforms
(let [c v10k]   (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 219.782220 µs
(let [c v100k]  (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 2.722521 ms
(let [c v1m]    (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 22.701385 ms
(let [c v10k]   (bench (p/min c (map inc) (filter odd?)))) ;; 168.950187 µs
(let [c v100k]  (bench (p/min c (map inc) (filter odd?)))) ;; 1.361213 ms
(let [c v1m]    (bench (p/min c (map inc) (filter odd?)))) ;; 12.085497 ms

;; experiments...
(let [c v1m]
  (bench
    (r/fold
      8000
      min
      (fn [v] (nth (sort v) 0))
      (reify r/CollFold
        (coll-fold [this n combinef f]
          (p/foldvec c n combinef f))))))
;; 647ms
