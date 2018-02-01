(ns bamap)

(require '[criterium.core :refer [quick-bench]])
(require '[parallel.core :as p] :reload)

(let [c (range 50000)] (quick-bench (doall (map inc c))))                             ;; 1.9
(let [c (to-array (range 50000))] (quick-bench (amap c idx ret (inc (aget c idx)))))  ;; 0.4
(let [c (to-array (range 50000))] (quick-bench (p/amap inc c)))                       ;; 0.6

(let [c (range 500000)] (quick-bench (doall (map inc c))))                            ;; 18
(let [c (to-array (range 500000))] (quick-bench (amap c idx ret (inc (aget c idx))))) ;; 4.9
(let [c (to-array (range 500000))] (quick-bench (p/amap inc c)))                      ;; 5.9

(let [c (range 2e6)] (quick-bench (doall (map inc c))))                               ;; 80
(let [c (to-array (range 2e6))] (quick-bench (amap c idx ret (inc (aget c idx)))))    ;; 20
(let [c (to-array (range 2e6))] (quick-bench (p/amap inc c)))                         ;; 18

(let [c (range 5e6)] (quick-bench (doall (map inc c))))                               ;; 201
(let [c (to-array (range 5e6))] (quick-bench (amap c idx ret (inc (aget c idx)))))    ;; 44
(let [c (to-array (range 5e6))] (quick-bench (p/amap inc c)))                         ;; 58

;; demanding f

(defn pi [n] (->> (range) (filter odd?) (take n) (map / (cycle [1 -1])) (reduce +) (* 4.0)))
(def pis (shuffle (range 400 800)))

(let [c pis] (time (dorun (map pi c))))                                               ;; 12178
(let [c (to-array pis)] (time (amap c idx ret (pi (aget c idx)))))                    ;; 11901
(let [c (to-array pis)] (time (p/amap pi c)))                                         ;; 6991
