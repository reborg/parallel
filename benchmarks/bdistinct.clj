(ns bdistinct)

(require '[criterium.core :refer [quick-benchmark]])
(defmacro b [expr] `(* 1000. (first (:mean (quick-benchmark ~expr {}))))) ;; mssecs
(require '[parallel.core :as p] :reload)

(defn create-with-uniques [percent n]
  (cond
    (== 0 percent) (take n (repeat 1))
    (== 100 percent) (shuffle (range n))
    :else (let [k (quot n percent)] (shuffle (apply concat (take (/ 1 (/ percent 100.)) (repeat (range (* n (/ percent 100.))))))))))

;; ballpark at 100k
(def coll 1e5)
(def c100 (create-with-uniques 100 coll))
(def c75 (create-with-uniques 75 coll))
(def c50 (create-with-uniques 50 coll))
(def c25 (create-with-uniques 25 coll))
(def c0 (create-with-uniques 0 coll))

;; normal core
(let [c (into [] c100)] (quick-bench (doall (distinct c)))) ; 76.321408 ms
(let [c (into [] c75)]  (quick-bench (doall (distinct c)))) ; 95.102771 ms
(let [c (into [] c50)]  (quick-bench (doall (distinct c)))) ; 59.967416 ms
(let [c (into [] c25)]  (quick-bench (doall (distinct c)))) ; 47.372695 ms
(let [c (into [] c0)]   (quick-bench (doall (distinct c)))) ; 26.161685 ms

;; normal core on sequences
(let [c c100] (quick-bench (doall (distinct c)))) ; 74.756156 ms
(let [c c75]  (quick-bench (doall (distinct c)))) ; 98.587782 ms
(let [c c50]  (quick-bench (doall (distinct c)))) ; 63.899022 ms
(let [c c25]  (quick-bench (doall (distinct c)))) ; 56.241547 ms
(let [c c0]   (quick-bench (doall (distinct c)))) ; 19.684880 ms

;; transducers core
(let [c (into [] c100)] (quick-bench (doall (sequence (distinct) c)))) ; 65.090661 ms
(let [c (into [] c75)]  (quick-bench (doall (sequence (distinct) c)))) ; 77.059407 ms
(let [c (into [] c50)]  (quick-bench (doall (sequence (distinct) c)))) ; 44.620541 ms
(let [c (into [] c25)]  (quick-bench (doall (sequence (distinct) c)))) ; 32.205828 ms
(let [c (into [] c0)]   (quick-bench (doall (sequence (distinct) c)))) ; 7.455225 ms

;; parallel on sequences
(let [c c100] (quick-bench (doall (p/distinct c)))) ; 7.677920 ms
(let [c c75]  (quick-bench (doall (p/distinct c)))) ; 8.686195 ms
(let [c c50]  (quick-bench (doall (p/distinct c)))) ; 4.875998 ms
(let [c c25]  (quick-bench (doall (p/distinct c)))) ; 4.980696 ms
(let [c c0]   (quick-bench (doall (p/distinct c)))) ; 11.416917 ms

;; parallel on vectors
(let [c (into [] c100)] (quick-bench (doall (p/distinct c)))) ; 7.391681 ms
(let [c (into [] c75)]  (quick-bench (doall (p/distinct c)))) ; 7.802467 ms
(let [c (into [] c50)]  (quick-bench (doall (p/distinct c)))) ; 4.966004 ms
(let [c (into [] c25)]  (quick-bench (doall (p/distinct c)))) ; 4.208700 ms
(let [c (into [] c0)]   (quick-bench (doall (p/distinct c)))) ; 8.037075 ms

;; parallel mutable
(binding [p/*mutable* true]
(let [c (into [] c100)] (quick-bench (p/distinct c)))  ; 2.739602 ms
(let [c (into [] c75)]  (quick-bench (p/distinct c)))  ; 6.188239 ms
(let [c (into [] c50)]  (quick-bench (p/distinct c)))  ; 3.679788 ms
(let [c (into [] c25)]  (quick-bench (p/distinct c)))  ; 2.713920 ms
(let [c (into [] c0)]   (quick-bench (p/distinct c)))) ; 7.802422 ms

