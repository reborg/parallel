(ns core-test
  (:import [clojure.lang RT] [java.io File] [java.util ArrayList])
  (:require [parallel.core :as p]
            [clojure.core.reducers :as r]
            [clojure.test :refer :all]))

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

  (testing "p/transduce"
    (let [v (vec (range 10000))]
      (is (= (reduce + 0 (filter odd? (map inc v)))
             (p/transduce (comp (map inc) (filter odd?)) + v)))))

  (testing "p/folding without reducing, just conj"
    (let [v (vec (range 10000))]
      (is (= (reduce conj [] (filter odd? (map inc v)))
             (r/fold
               (r/monoid into (constantly []))
               ((comp (map inc) (filter odd?)) conj) v)
             (p/fold
               (r/monoid into (constantly []))
               ((comp (map inc) (filter odd?)) conj) v)))))

  (testing "hashmaps, not just vectors"
    (is (= {\a [21] \z [23] \h [10 12]}
           (p/fold
             (r/monoid #(merge-with into %1 %2) (constantly {}))
             (fn [m [k v]]
               (let [c (Character/toLowerCase ^Character (first k))]
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
    (let [coll (reverse (range 1000))
          c2 (shuffle (map (comp str char) (range 65 91)))]
      (is (= (range 1000) (p/sort 200 < coll)))
      (is (= coll (p/sort 200 > coll)))
      (is (= (sort compare c2) (p/sort compare c2))))))

;; (int (/ 100000 (Math/pow 2 8)))
(deftest external-sorting
  (testing "sanity"
    (let [coll (into [] (reverse (range 1000)))]
      (is (= 0
             (first (p/external-sort 125 compare identity coll))))))
  (testing "additional processing"
    (let [coll (map #(str % "-" %) (range 100000))
          fetchf (fn [c] (map #(clojure.string/split % #"-") c))]
      (is (= ["99999" "99999"]
             (first (p/external-sort 1562 #(compare (peek %2) (peek %1)) fetchf coll)))))))

(deftest min-max
  (testing "min"
    (let [c (shuffle (conj (range 100000) -3))]
      (is (= -3 (p/min c)))))
  (testing "max"
    (let [c (shuffle (conj (range 100000) -3))]
      (is (= 99999 (p/max c)))))
  (testing "xducers"
    (let [c (into [] (shuffle (conj (range 100000) -3)))]
      (is (= 99998 (p/max c (map dec))))))
  (testing "min-index"
    (let [c (conj (range 100000) -3)]
      (is (= 99999 (p/max c))))))

(deftest pamap-test
  (testing "sanity"
    (let [c (to-array (range 100000))]
      (is (= (map inc (range 10)) (take 10 (p/amap inc c)))))))

(deftest distinct-test
  (let [c (shuffle (apply concat (take 5 (repeat (range 10000)))))]
    (testing "sanity"
      (is (= (sort (distinct c)) (sort (p/distinct c)))))
    (testing "with transducers"
      (is (= [1 3 5 7 9] (take 5 (sort (p/distinct c (map inc) (filter odd?)))))))
    (testing "equality semantic"
      (is (= (sort (distinct (map vector c c)))
             (sort (p/distinct (map vector c c))))))
    (testing "mutability on"
      (is (= #{1 2 3}
             (into #{} (binding [p/*mutable* true] (p/distinct [1 2 3]))))))))

(deftest reverse-test
  (testing "swap reverse simmetrical regions in arrays"
    (let [s (range 10)]
      (is (= s (let [a (object-array s)] (p/arswap identity 0 9 0 a) (into [] a))))
      (is (= (reverse s) (let [a (object-array s)] (p/arswap identity 0 9 5 a) (into [] a))))
      (is (= (reverse s) (let [a (object-array s)] (p/arswap identity 0 9 10 a) (into [] a))))
      (is (= [9 8 2 3 4 5 6 7 1 0] (let [a (object-array s)] (p/arswap identity 0 9 2 a) (into [] a))))
      (is (= [9 8 7 6 5 4 3 2 1] (let [a (object-array (rest s))] (p/arswap identity 0 8 4 a) (into [] a))))
      (is (= [9 8 7 4 5 6 3 2 1] (let [a (object-array (rest s))] (p/arswap identity 0 8 3 a) (into [] a))))))
  (testing "swap reverse with transform"
    (let [s (range 10)]
      (is (= ["9" "8" 2 3 4 5 6 7 "1" "0"] (let [a (object-array s)] (p/arswap str 0 9 2 a) (into [] a))))
      (is (= [:9 :8 :7 4 5 6 :3 :2 :1] (let [a (object-array (rest s))] (p/arswap (comp keyword str) 0 8 3 a) (into [] a))))))
  (testing "sanity"
    (is (= nil (p/armap identity nil)))
    (is (= (reverse ()) (let [a (object-array ())] (p/armap identity a) (into [] a))))
    (is (= (reverse (range 1)) (let [a (object-array (range 1))] (p/armap identity a) (into [] a))))
    (is (= (reverse (range 5)) (let [a (object-array (range 5))] (p/armap identity a) (into [] a))))
    (is (= (reverse (range 1e2)) (let [a (object-array (range 1e2))] (p/armap identity a) (into [] a))))
    (let [xs (shuffle (range 11))
          a (object-array xs)]
      (is (= (reverse (map str xs)) (do (p/armap str a) (into [] a)))))))

(deftest slurping
  (testing "slurping sanity"
    (is (= (slurp "test/words") (p/slurp (File. "test/words"))))))

(deftest parallel-let
  (testing "it works like normal let"
    (is (= 3 (p/let [a 1 b 2] (+ a b))))
    (is (= 3 (p/let [a (future 1) b (future 2)] (+ @a @b))))
    (is (= 6 (p/let [[a b] [1 2] {c :c} {:c 3}] (+ a b c))))
    (is (= 300 (p/let [a (do (Thread/sleep 20) 100) b (do (Thread/sleep 10) 200)] (+ a b))))))

(deftest parallel-args
  (testing "works like a standard function invocation"
    (is (= 6 (p/args + 1 2 3)))
    (is (= 1 (p/args first [1 2 3])))
    (is (= 300 (p/args + (do (Thread/sleep 20) 100) (do (Thread/sleep 10) 200))))))

(deftest parallel-and
  (testing "works like a standard and"
    (is (= (and) (p/and)))
    (is (= "a"   (p/and true 1 "a")))
    (is (= :x    (p/and :y true :x)))
    (is (= false (p/and true false true)))
    (is (p/and (do (Thread/sleep 20) true) (do (Thread/sleep 10) true)))))

(deftest parallel-or
  (testing "works like a standard or"
    (is (= (or)  (p/or)))
    (is (= true  (p/or true 1 "a")))
    (is (= :y    (p/or false :y true :x)))
    (is (= true  (p/or true false true)))
    (is (p/or (do (Thread/sleep 20) false) (do (Thread/sleep 10) true)))))

  ; TODO: need fixing or docs. Shared coll needs to be concurrent.
  ; (distinct (repeatedly 100 #(let* [G__3879 (new ArrayList)]
  ;   (let* [G__3883 (clojure.core/future-call (fn* [] (. G__3879 add 1)))
  ;          G__3884 (clojure.core/future-call (fn* [] (. G__3879 add 2)))]
  ;     (let* [G__3880 (deref G__3883) G__3881 (deref G__3884)] G__3881))
  ;   G__3879)))

(deftest parallel-do-doto
  (testing "like do, but forms evaluated in parallel."
    (is (= nil (p/do)))
    (is (= 1 (p/do 1)))
    (is (= [1 2] (let [a (ArrayList.)] (p/do (.add a 1) (.add a 2) (vec a)))))
    (is (= [1 2] (let [a (ArrayList.)] (p/do (.add a 1) (.add a 2)) (vec a)))))
  (testing "like doto, but forms evaluated in parallel."
    (is (= 1 (p/doto 1)))
    (is (= [1 2] (vec (p/doto (ArrayList.) (.add 1) (.add 2)))))))
