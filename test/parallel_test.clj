(ns parallel-test
  (:import [clojure.lang RT])
  (:require [parallel :as p]
            [clojure.test :refer :all]))

(deftest eduction-sequence-test
  (testing "eduction"
    (is (= [1 3 5 7 9] (p/eduction (map inc) (filter odd?) (range 10)))))

  (testing "sequence"
    (is (= [1 3 5 7 9] (p/sequence (comp (map inc) (filter odd?)) (range 10))))
    (is (= (range 10) (p/sequence (comp (map vector) cat (dedupe)) (range 10) (range 10)))))

  (testing "clj-1669"
    (let [s (range 1000)
          v (vec s)
          s50 (range 50)
          v50 (vec s50)]
      (is (= (into [] (->> s (p/eduction (interpose 5) (partition-all 2)))) (into [] (->> s (eduction (interpose 5) (partition-all 2))))))
      (is (= (p/sequence (map inc) s) (sequence (map inc) s)))
      (is (= (into [] (p/eduction (map inc) s)) (into [] (eduction (map inc) s))))
      (is (= (p/sequence (comp (map inc) (map inc)) s) (sequence (comp (map inc) (map inc)) s)))
      (is (= (into [] (p/eduction (map inc) (map inc) s)) (into [] (eduction (map inc) (map inc) s))))
      (is (= (p/sequence (comp (map inc) (mapcat range)) s50) (sequence (comp (map inc) (mapcat range)) s50)))
      (is (= (into [] (p/eduction (map inc) (mapcat range) s50)) (into [] (eduction (map inc) (mapcat range) s50))))
      (is (= (map inc (p/eduction (map inc) s)) (map inc (eduction (map inc) s))))
      (is (= (map inc (p/eduction (map inc) (map inc) s)) (map inc (eduction (map inc) (map inc) s))))
      (is (= (sort (p/eduction (map inc) s)) (sort (eduction (map inc) s))))
      (is (= (->> s (p/eduction (filter odd?) (map str)) (sort-by last)) (->> s (eduction (filter odd?) (map str)) (sort-by last))))
      )))

(deftest interleave-test
  (testing "interleave with sequence"
    (is (= [0 :a 1 :b 2 :c] (sequence (p/interleave [:a :b :c]) (range 3))))
    (are [x y] (= x y)
         (sequence (p/interleave [1 2]) [3 4]) (interleave [3 4] [1 2])
         (sequence (p/interleave [1]) [3 4]) (interleave [3 4] [1])
         (sequence (p/interleave [1 2]) [3]) (interleave [3] [1 2])
         (sequence (p/interleave []) [3 4]) (interleave [3 4] [])
         (sequence (p/interleave [1 2]) []) (interleave [] [1 2])
         (sequence (p/interleave []) []) (interleave [] [])))
  (testing "interleave with eduction"
    (is (= [1 0 2 1 3 2 4 3 5 4 6 5 7 6 8 7 9 8 10 9]
           (eduction (map inc) (p/interleave (range)) (filter number?) (range 10))))))

(deftest frequencies-test
  (testing "frequencies with xform"
    (is (= 5000 (count (p/frequencies (range 1e4) (filter odd?)))))
    (is (= {":a" 2 ":b" 3} (p/frequencies [:a :a :b :b :b] (map str)))))
  (testing "misc examples"
    (are [expected test-seq] (= (p/frequencies test-seq) expected)
         {\p 2 \s 4 \i 4 \m 1} "mississippi"
         {1 4 2 2 3 1} [1 1 1 1 2 2 3]
         {1 3 2 2 3 1} [1 1 1 2 2 3]
         {1 4 2 2 3 1} '(1 1 1 1 2 2 3))))

(defn large-map [i] (into {} (map vector (range i) (range i))))

(deftest update-vals-test
  (testing "sanity"
    (is (= (map inc (range 1000))
           (sort (vals (p/update-vals (large-map 1000) inc)))))))
