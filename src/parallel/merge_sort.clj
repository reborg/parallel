(ns parallel.merge-sort
  (:refer-clojure :exclude [sort])
  (:require [clojure.core.reducers :as r])
  (:import
    [java.util.concurrent Callable ForkJoinPool]
    [java.util Arrays Comparator]))

(set! *warn-on-reflection* true)

(definterface IMergeSort
  (merge [mid])
  (sort []))

(deftype MergeSort [^objects a
                    ^int lo
                    ^int hi
                    ^int threshold
                    ^Comparator cmp]

  Callable
  (call [this] (.sort this))

  IMergeSort
  (merge [this mid]
    (when (pos? (.compare cmp (aget a (dec mid)) (aget a mid)))
      (let [size (- hi lo)
            lsize (- mid lo)
            ^objects aux (object-array size)]
        (System/arraycopy a lo aux 0 size)
        (loop [k lo i 0 j lsize]
          (when (< k hi)
            (if (or (>= j size) (and (< i lsize) (neg? (.compare cmp (aget aux i) (aget aux j)))))
              (do (aset a k (aget aux i)) (recur (inc k) (inc i) j))
              (do (aset a k (aget aux j)) (recur (inc k) i (inc j)))))))))

  (sort [this]
    (let [size (- hi lo)]
      (if (<= size threshold)
        (Arrays/sort a lo hi cmp)
        (let [mid (+ lo (bit-shift-right size 1))
              l (MergeSort. a lo mid threshold cmp)
              h (MergeSort. a mid hi threshold cmp)]
          (let [fc (fn [^Callable child] #(.call child))]
            (#'r/fjinvoke
              #(let [f1 (fc l)
                     t2 (#'r/fjtask (fc h))]
                 (#'r/fjfork t2)
                 (f1)
                 (#'r/fjjoin t2)
                 (.merge this mid)))))))))

(defn sort [threshold cmp ^objects a]
  (let [n (alength a)
        ^ForkJoinPool pool @r/pool]
    (.join (.submit pool (MergeSort. a 0 n threshold cmp)))))
