(ns xduce-test
  (:refer-clojure :exclude [eduction sequence])
  (:import [clojure.lang RT])
  (:require [xduce :refer :all]
            [clojure.test :refer :all]))

(deftest eduction-test
  (testing "eduction"
    (is (= [1 3 5 7 9] (eduction (map inc) (filter odd?) (range 10))))))

(deftest sequence-test
  (testing "sequence"
    (is (= [1 3 5 7 9] (sequence (comp (map inc) (filter odd?)) (range 10))))
    (is (= (range 10) (sequence (comp (map vector) cat (dedupe)) (range 10) (range 10))))))

(deftest mix
(testing "clj-1669"
  (let [s (range 1000)
        v (vec s)
        s50 (range 50)
        v50 (vec s50)]
    (is (= (into [] (->> s (eduction (interpose 5) (partition-all 2)))) (into [] (->> s (clojure.core/eduction (interpose 5) (partition-all 2))))))
    (is (= (sequence (map inc) s) (clojure.core/sequence (map inc) s)))
    (is (= (into [] (eduction (map inc) s)) (into [] (clojure.core/eduction (map inc) s))))
    (is (= (sequence (comp (map inc) (map inc)) s) (clojure.core/sequence (comp (map inc) (map inc)) s)))
    (is (= (into [] (eduction (map inc) (map inc) s)) (into [] (clojure.core/eduction (map inc) (map inc) s))))
    (is (= (sequence (comp (map inc) (mapcat range)) s50) (clojure.core/sequence (comp (map inc) (mapcat range)) s50)))
    (is (= (into [] (eduction (map inc) (mapcat range) s50)) (into [] (clojure.core/eduction (map inc) (mapcat range) s50))))
    (is (= (map inc (eduction (map inc) s)) (map inc (clojure.core/eduction (map inc) s))))
    (is (= (map inc (eduction (map inc) (map inc) s)) (map inc (clojure.core/eduction (map inc) (map inc) s))))
    (is (= (sort (eduction (map inc) s)) (sort (clojure.core/eduction (map inc) s))))
    (is (= (->> s (eduction (filter odd?) (map str)) (sort-by last)) (->> s (clojure.core/eduction (filter odd?) (map str)) (sort-by last))))
    )))
