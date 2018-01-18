(ns parallel.merge-sort-test
  (:require [parallel.merge-sort :as msort]
            [clojure.test :refer :all])
  (:import [parallel.merge_sort MergeSort]
           [java.util Arrays]))

(deftest parallel-merge-sort
  (testing "with numbers"
    (let [n 10000
          v (into [] (shuffle (range n)))
          a1 (object-array v)
          a2 (object-array v)]
      (is (= (do (Arrays/parallelSort a2 0 n compare) (into [] a2))
             (do (.join (.submit @msort/pool (MergeSort. a1 0 n @msort/pool 8192 compare))) (into [] a1))))))
  (testing "with strings"
    (let [n 10000
          v (into [] (map str (shuffle (range n))))
          a1 (object-array v)
          a2 (object-array v)]
      (is (= (do (Arrays/parallelSort a2 0 n compare) (into [] a2))
             (do (.join (.submit @msort/pool (MergeSort. a1 0 n @msort/pool 8192 compare))) (into [] a1)))))))
