(ns bsort)

(require '[criterium.core :refer [quick-benchmark]])
(defmacro b [expr] `(* 1000. (first (:mean (quick-benchmark ~expr {}))))) ;; mssecs
(require '[parallel :as p] :reload)
(import '[java.util Arrays])

(defn sort-some [percent coll]
  (cond
    (== 100 percent) coll
    (== 0 percent) (let [n (count coll) half (quot n 2)] (interleave (take half coll) (reverse (drop half coll))))
    :else (apply concat (map #(if (< (rand) (/ percent 100.)) (sort %) %) (partition-all 20 (shuffle coll))))))

(def coll (range 1e6))
(def cmp (comparator #(neg? (compare (last %1) (last %2)))))

(let [c (into [] (sort-some 100 coll))] (b (sort c)))
(let [c (into [] (sort-some 95  coll))] (b (sort c)))
(let [c (into [] (sort-some 50  coll))] (b (sort c)))
(let [c (into [] (sort-some 10  coll))] (b (sort c)))
(let [c (into [] (sort-some 0   coll))] (b (sort c)))

29.374563875000003
829.5600573333334
893.2477856666666
867.0599766666667
104.929119

(let [c (into [] (sort-some 100 coll))] (b (p/sort c)))
(let [c (into [] (sort-some 95  coll))] (b (p/sort c)))
(let [c (into [] (sort-some 50  coll))] (b (p/sort c)))
(let [c (into [] (sort-some 10  coll))] (b (p/sort c)))
(let [c (into [] (sort-some 0   coll))] (b (p/sort c)))

67.0043625
1085.0402506666667
-739.7066338333335
-681.320965
241.27001366666667

(let [c (into [] (sort-some 100 coll))] (binding [p/*mutable* true] (b (p/sort c))))
(let [c (into [] (sort-some 95  coll))] (binding [p/*mutable* true] (b (p/sort c))))
(let [c (into [] (sort-some 50  coll))] (binding [p/*mutable* true] (b (p/sort c))))
(let [c (into [] (sort-some 10  coll))] (binding [p/*mutable* true] (b (p/sort c))))
(let [c (into [] (sort-some 0   coll))] (binding [p/*mutable* true] (b (p/sort c))))

41.78496162500001
-803.7370548333334
-729.7513658333335
-689.6999491666667
200.83075633333337

(let [c (into [] (sort-some 100 (map str coll)))] (b (sort c)))
(let [c (into [] (sort-some 95  (map str coll)))] (b (sort c)))
(let [c (into [] (sort-some 50  (map str coll)))] (b (sort c)))
(let [c (into [] (sort-some 10  (map str coll)))] (b (sort c)))
(let [c (into [] (sort-some 0   (map str coll)))] (b (sort c)))

60.442482250000005
1008.2496673333334
954.3344250000001
1032.2275173333335
210.32057850000001

(let [c (into [] (sort-some 100 (map str coll)))] (b (p/sort c)))
(let [c (into [] (sort-some 95  (map str coll)))] (b (p/sort c)))
(let [c (into [] (sort-some 50  (map str coll)))] (b (p/sort c)))
(let [c (into [] (sort-some 10  (map str coll)))] (b (p/sort c)))
(let [c (into [] (sort-some 0   (map str coll)))] (b (p/sort c)))

154.14334116666666
-835.3246303333334
-719.7321315
-860.1659333333334
285.89856916666673

(def coll (range 1e5))

(let [c (into [] (sort-some 100 (map-indexed vector coll)))] (b (sort cmp c)))
(let [c (into [] (sort-some 95  (map-indexed vector coll)))] (b (sort cmp c)))
(let [c (into [] (sort-some 50  (map-indexed vector coll)))] (b (sort cmp c)))
(let [c (into [] (sort-some 10  (map-indexed vector coll)))] (b (sort cmp c)))
(let [c (into [] (sort-some 0   (map-indexed vector coll)))] (b (sort cmp c)))

51.04247650000001
756.7503003333335
736.9798181666667
714.490584500000
193.05705116666667

(let [c (into [] (sort-some 100 (map-indexed vector coll)))] (b (p/sort cmp c)))
(let [c (into [] (sort-some 95  (map-indexed vector coll)))] (b (p/sort cmp c)))
(let [c (into [] (sort-some 50  (map-indexed vector coll)))] (b (p/sort cmp c)))
(let [c (into [] (sort-some 10  (map-indexed vector coll)))] (b (p/sort cmp c)))
(let [c (into [] (sort-some 0   (map-indexed vector coll)))] (b (p/sort cmp c)))

-8.597955738095237
-67.84706525
-60.098097583333335
-59.493946750000006
-31.742734066666667

(def coll (range 1e6))
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
