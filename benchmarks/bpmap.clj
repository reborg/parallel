(ns bpmap)

(require '[criterium.core :refer [quick-bench]])
(require '[parallel :as p] :reload)

(def coll0 (range 50000))

(let [c coll0] (quick-bench (doall (pmap inc c))))      ;; 130
(let [c coll0] (quick-bench (doall (map inc c))))       ;; 1.8
(let [c coll0] (quick-bench (p/pmap inc c)))            ;; 3.0
(let [c (into [] coll0)] (quick-bench (p/pmap inc c)))  ;; 2.6
(let [c (into [] coll0)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c))))  ;; 1.0
(let [c (object-array coll0)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c))))  ;; 0.7

(def coll1 (range 100000))

(let [c coll1] (quick-bench (doall (pmap inc c))))      ;; 361
(let [c coll1] (quick-bench (doall (map inc c))))       ;; 5.1
(let [c coll1] (quick-bench (p/pmap inc c)))            ;; 6.25
(let [c (into [] coll1)] (quick-bench (p/pmap inc c)))  ;; 4.5
(let [c (into [] coll1)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c))))  ;; 2.03

(def coll2 (range 1000000))

(let [c coll2] (quick-bench (doall (map inc c))))       ;; 37
(let [c coll2] (quick-bench (p/pmap inc c)))            ;; 63
(let [c (into [] coll2)] (quick-bench (p/pmap inc c)))  ;; 58
(let [c (into [] coll2)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c))))  ;; 21

(def coll3 (range 3000000))

(let [c coll3] (quick-bench (doall (map inc c))))       ;; 223
(let [c coll3] (quick-bench (p/pmap inc c)))            ;; 280
(let [c (into [] coll3)] (quick-bench (p/pmap inc c)))  ;; 173
(let [c (into [] coll3)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c))))  ;; 61

(def coll4 (range 5000000))

(let [c coll4] (quick-bench (doall (map inc c))))       ;; 184
(let [c coll4] (quick-bench (p/pmap inc c)))            ;; 275
(let [c (into [] coll4)] (quick-bench (binding [p/*mutable* true] (p/pmap inc c)))) ;; 100

;; demanding f:

(defn pi [n] (->> (range) (filter odd?) (take n) (map / (cycle [1 -1])) (reduce +) (* 4.0)))
(def pis (shuffle (range 600 800)))

(let [c pis] (time (dorun (pmap pi c))))    ;; 4821 ms
(let [c pis] (time (do (p/pmap 1 pi c) nil))) ;; 4221 ms
