(ns xduce-test
  (:import [clojure.lang RT])
  (:require [xduce :refer :all]
            [clojure.test :refer :all]))

(deftest eduction-test
  (testing "eduction"
    (is (= [1 3 5 7 9] (xeduction (map inc) (filter odd?) (range 10))))))

(deftest sequence-test
  (testing "sequence"
    (is (= [1 3 5 7 9] (xequence (comp (map inc) (filter odd?)) (range 10))))
    (is (= (range 10) (xequence (comp (map vector) cat (dedupe)) (range 10) (range 10))))))

(deftest mix
(testing "clj-1669"
  (let [s (range 1000)
        v (vec s)
        s50 (range 50)
        v50 (vec s50)]
    (is (= (into [] (->> s (xeduction (interpose 5) (partition-all 2)))) (into [] (->> s (eduction (interpose 5) (partition-all 2))))))
    (is (= (xequence (map inc) s) (sequence (map inc) s)))
    (is (= (into [] (xeduction (map inc) s)) (into [] (eduction (map inc) s))))
    (is (= (xequence (comp (map inc) (map inc)) s) (sequence (comp (map inc) (map inc)) s)))
    (is (= (into [] (xeduction (map inc) (map inc) s)) (into [] (eduction (map inc) (map inc) s))))
    (is (= (xequence (comp (map inc) (mapcat range)) s50) (sequence (comp (map inc) (mapcat range)) s50)))
    (is (= (into [] (xeduction (map inc) (mapcat range) s50)) (into [] (eduction (map inc) (mapcat range) s50))))
    (is (= (map inc (xeduction (map inc) s)) (map inc (eduction (map inc) s))))
    (is (= (map inc (xeduction (map inc) (map inc) s)) (map inc (eduction (map inc) (map inc) s))))
    (is (= (sort (xeduction (map inc) s)) (sort (eduction (map inc) s))))
    (is (= (->> s (xeduction (filter odd?) (map str)) (sort-by last)) (->> s (eduction (filter odd?) (map str)) (sort-by last))))
    )))
