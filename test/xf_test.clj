(ns xf-test
  (:require [parallel.xf :as xf]
            [clojure.test :refer :all]))

(deftest interleave-test
  (testing "interleave with sequence"
    (is (= [0 :a 1 :b 2 :c] (sequence (xf/interleave [:a :b :c]) (range 3))))
    (are [x y] (= x y)
         (sequence (xf/interleave [1 2]) [3 4]) (interleave [3 4] [1 2])
         (sequence (xf/interleave [1]) [3 4]) (interleave [3 4] [1])
         (sequence (xf/interleave [1 2]) [3]) (interleave [3] [1 2])
         (sequence (xf/interleave []) [3 4]) (interleave [3 4] [])
         (sequence (xf/interleave [1 2]) []) (interleave [] [1 2])
         (sequence (xf/interleave []) []) (interleave [] [])))
  (testing "interleave with eduction"
    (is (= [1 0 2 1 3 2 4 3 5 4 6 5 7 6 8 7 9 8 10 9]
           (eduction (map inc) (xf/interleave (range)) (filter number?) (range 10))))))

(deftest pmap-test
  (testing "pmap as a transducer, similarly to map"
    (is (= 250000
           (transduce
             (comp
               (xf/pmap inc)
               (filter odd?)) +
             (range 1000))))
    (is (= (sequence
             (comp
               (filter odd?)
               (map #(* % %))
               (take 10))
             (range 1000))
           (sequence
             (comp
               (filter odd?)
               (xf/pmap #(* % %))
               (take 10))
             (range 1000))))))

(deftest identity-test
  (testing "single"
    (is (= (range 10) (sequence xf/identity (range 10))))
    (is (= (range 1 11) (sequence (comp (map inc) xf/identity) (range 10))))
    (is (= (range 1 11) (sequence (comp xf/identity (map inc)) (range 10))))
    (is (= [2 4 6 8 10] (sequence (comp (filter odd?) xf/identity (map inc)) (range 10)))))

  (testing "multi"
    (is (= (map vector (range 10) (range 10))
           (sequence xf/identity (range 10) (range 10))))
    (is (= (range 0 20 2)
           (sequence
             (comp (map #(+ %1 %2))
                   xf/identity)
             (range 10) (range 10))))
    (is (= (range 0 20 2)
           (sequence
             (comp xf/identity
                   (map #(apply + %)))
             (range 10) (range 10))))
    (is (= [1 1 2 2 3 3 4 4 5 5]
           (sequence
             (comp xf/identity
                   cat
                   (map inc))
             (range 5) (range 5))))
    (is (= (range 0 20 2)
           (sequence
             (comp (map vector)
                   xf/identity
                   (map #(apply + %)))
             (range 10) (range 10))))
    ))
