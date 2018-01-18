(ns parallel-test
  (:import [clojure.lang RT])
  (:require [parallel :as p]
            [clojure.core.reducers :as r]
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
  (testing "a dictionary of words with no dupes"
    (let [dict (slurp "test/words")]
      (is (= (count (re-seq #"\S+" dict))
             (->> dict
                  (re-seq #"\S+")
                  (frequencies)
                  (map second)
                  (reduce +))))))
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

(defmacro repeater [& forms]
  `(first (distinct (for [i# (range 500)] (do ~@forms)))))

(defn chunkedf [f rf size coll]
  (->> coll (partition-all size) (mapcat f) (reduce rf)))

(deftest stateful-transducers
  (testing "should drop based on chunk size"
    (is (= (chunkedf #(drop 10 %) + 200 (vec (range 1600)))
           (repeater (r/fold 200 + (p/xrf + (drop 10)) (p/folder (vec (range 1600)))))))
    (is (= (chunkedf #(drop 10 %) + 100 (vec (range 204800)))
           (repeater (r/fold 100 + (p/xrf + (drop 10)) (p/folder (vec (range 204800)))))))
    (is (= (chunkedf #(drop 10 %) + 400 (vec (range 1600)))
           (repeater (r/fold + (p/xrf + (drop 10)) (p/folder (vec (range 1600))))))))
  (testing "folding by number of chunks"
    (is (= [3  4  5  6  7  8  9  10 11 12
            16 17 18 19 20 21 22 23 24 25
            29 30 31 32 33 34 35 36 37 38
            42 43 44 45 46 47 48 49 50 51]
           (r/fold "ignored"
                   (r/monoid concat conj)
                   (p/xrf conj (drop 3))
                   (p/folder (vec (range 52)) 4))))
    (is (= (- 1802 (* 3 8))
           (count (r/fold "ignored"
                          (r/monoid concat conj)
                          (p/xrf conj (drop 3))
                          (p/folder (vec (range 1802)) 8))))))
  (testing "p/fold entry point at 32 default chunks"
    (is (= (chunkedf #(drop 10 %) + (/ 2048 32) (vec (map inc (range 2048))))
           (p/fold (p/xrf + (drop 10) (map inc)) (vec (range 2048))))))
  (testing "p/fold VS r/fold on stateless xducers should be the same"
    (let [v (vec (range 10000))]
      (is (= (r/fold + ((comp (map inc) (filter odd?)) +) v)
             (p/fold (p/xrf + (map inc) (filter odd?)) v)
             (p/fold + ((comp (map inc) (filter odd?)) +) v)))))
  (testing "hashmaps, not just vectors"
    (is (= {\a [21] \z [23] \h [10 12]}
           (p/fold
             (r/monoid #(merge-with into %1 %2) (constantly {}))
             (fn [m [k v]]
               (let [c (Character/toLowerCase (first k))]
                 (assoc m c (conj (get m c []) v))))
             (hash-map "abba" 21 "zubb" 23 "hello" 10 "hops" 12)))))
  (testing "folding hashmaps with transducers"
    (is (= {0 1 1 2 2 3 3 4}
           (p/fold
             (r/monoid merge (constantly {}))
             (p/xrf conj (map (fn [[k v]] [k (inc v)])))
             (hash-map 0 0 1 1 2 2 3 3)))))
  (testing "exercising all code with larger maps"
    (is (= 999
           ((p/fold
             (r/monoid merge (constantly {}))
             (p/xrf conj
                    (filter (fn [[k v]] (even? k)))
                    (map (fn [[k v]] [k (inc v)])))
             (zipmap (range 10000) (range 10000))) 998)))))

(deftest counting
  (testing "count a coll"
    (is (= 100000 (p/count (map inc) (range 1e5))))
    (is (= (reduce + (range 50)) (p/count (comp (mapcat range)) (range 50))))))

(deftest grouping
  (testing "sanity"
    (is (= 5000 (count ((p/group-by odd? (range 10000)) true)))))
(testing "with xducers"
    (is (= 1667 (count ((p/group-by odd? (range 10000) (map inc) (filter #(zero? (mod % 3)))) true)))))
(testing "with stateful xducers"
    (is (= 1133 (count ((p/group-by odd? (range 10000) (drop 100) (map inc) (filter #(zero? (mod % 3)))) true)))))
  (testing "anagrams"
    (let [dict (slurp "test/words")]
      (is (= #{"caret" "carte" "cater" "crate"
               "creat" "creta" "react" "recta" "trace"}
             (into #{}
                   (->> dict
                        (re-seq #"\S+")
                        (p/group-by sort)
                        (sort-by (comp count second) >)
                        (map second)
                        first)))))))

(deftest sorting
  (testing "sanity"
    (let [coll (reverse (range 1000))]
      (is (= (range 1000)
             (p/sort 200 (comparator <) coll))))))

(deftest external-sorting
  (testing "sanity"
    (let [coll (into [] (reverse (range 1000)))]
      (is (= (range 1000)
             (p/external-sort 200 compare identity coll))))))
