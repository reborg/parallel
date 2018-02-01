(ns bsort)

(require '[criterium.core :refer [quick-benchmark]])
(defmacro b [expr] `(* 1000. (first (:mean (quick-benchmark ~expr {}))))) ;; mssecs
(require '[parallel.core :as p] :reload)
(import '[java.util Arrays])

(defn sort-some [percent coll]
  (cond
    (== 100 percent) coll
    (== 0 percent) (let [n (count coll) half (quot n 2)] (interleave (take half coll) (reverse (drop half coll))))
    :else (apply concat (map #(if (< (rand) (/ percent 100.)) (sort %) %) (partition-all 20 (shuffle coll))))))

;; ballpark at 1M
(def coll (range 1e6))

(let [c (into [] (sort-some 100 coll))] (b (sort c))) ;  25
(let [c (into [] (sort-some 95  coll))] (b (sort c))) ; 537
(let [c (into [] (sort-some 50  coll))] (b (sort c))) ; 781
(let [c (into [] (sort-some 10  coll))] (b (sort c))) ; 801
(let [c (into [] (sort-some 0   coll))] (b (sort c))) ; 132

(let [c (into [] (sort-some 100 coll))] (b (p/sort c))) ;  44
(let [c (into [] (sort-some 95  coll))] (b (p/sort c))) ; 502
(let [c (into [] (sort-some 50  coll))] (b (p/sort c))) ; 707
(let [c (into [] (sort-some 10  coll))] (b (p/sort c))) ; 675
(let [c (into [] (sort-some 0   coll))] (b (p/sort c))) ; 376

(let [c (into [] (sort-some 100 coll))] (binding [p/*mutable* true] (b (p/sort c)))) ; 19
(let [c (into [] (sort-some 95  coll))] (binding [p/*mutable* true] (b (p/sort c)))) ; 562
(let [c (into [] (sort-some 50  coll))] (binding [p/*mutable* true] (b (p/sort c)))) ; 548
(let [c (into [] (sort-some 10  coll))] (binding [p/*mutable* true] (b (p/sort c)))) ; 571
(let [c (into [] (sort-some 0   coll))] (binding [p/*mutable* true] (b (p/sort c)))) ; 292

;; heavier comparator, just vaguely faster than sequential.

(let [c (into [] (sort-some 100 (map str coll)))] (b (sort compare c))) ; 59
(let [c (into [] (sort-some 95  (map str coll)))] (b (sort compare c))) ; 760
(let [c (into [] (sort-some 50  (map str coll)))] (b (sort compare c))) ; 760
(let [c (into [] (sort-some 10  (map str coll)))] (b (sort compare c))) ; 802
(let [c (into [] (sort-some 0   (map str coll)))] (b (sort compare c))) ; 136

(let [c (into [] (sort-some 100 (map str coll)))] (b (p/sort compare c))) ; 136
(let [c (into [] (sort-some 95  (map str coll)))] (b (p/sort compare c))) ; 689
(let [c (into [] (sort-some 50  (map str coll)))] (b (p/sort compare c))) ; 740
(let [c (into [] (sort-some 10  (map str coll)))] (b (p/sort compare c))) ; 664
(let [c (into [] (sort-some 0   (map str coll)))] (b (p/sort compare c))) ; 258

;; Even heavier comparator
(def cmp #(compare (last %1) (last %2)))

(let [c (into [] (sort-some 100 (map-indexed vector coll)))] (b (sort cmp c))) ; 325
(let [c (into [] (sort-some 95  (map-indexed vector coll)))] (b (sort cmp c))) ; 6475
(let [c (into [] (sort-some 50  (map-indexed vector coll)))] (b (sort cmp c))) ; 6801
(let [c (into [] (sort-some 10  (map-indexed vector coll)))] (b (sort cmp c))) ; 6566
(let [c (into [] (sort-some 0   (map-indexed vector coll)))] (b (sort cmp c))) ; 1261

(let [c (into [] (sort-some 100 (map-indexed vector coll)))] (b (p/sort cmp c))) ; 182
(let [c (into [] (sort-some 95  (map-indexed vector coll)))] (b (p/sort cmp c))) ; 3589
(let [c (into [] (sort-some 50  (map-indexed vector coll)))] (b (p/sort cmp c))) ; 3371
(let [c (into [] (sort-some 10  (map-indexed vector coll)))] (b (p/sort cmp c))) ; 3422
(let [c (into [] (sort-some 0   (map-indexed vector coll)))] (b (p/sort cmp c))) ; 615

(set! *warn-on-reflection* true)
(let [c (int-array (sort-some 100 coll))] (b (do (Arrays/parallelSort c) (into [] c))))
(let [c (int-array (sort-some 95  coll))] (b (do (Arrays/parallelSort c) (into [] c))))
(let [c (int-array (sort-some 50  coll))] (b (do (Arrays/parallelSort c) (into [] c))))
(let [c (int-array (sort-some 10  coll))] (b (do (Arrays/parallelSort c) (into [] c))))
(let [c (int-array (sort-some 0   coll))] (b (do (Arrays/parallelSort c) (into [] c))))

39.43213305555556
38.128529944444445
38.26176866666667
42.11502133333334
39.757541388888896

(let [c (into [] (sort-some 50 coll))] (b (p/sort 5000 compare c)))
(let [c (into [] (sort-some 50 coll))] (b (p/sort 10000 compare c)))
(let [c (into [] (sort-some 50 coll))] (b (p/sort 15000 compare c)))
