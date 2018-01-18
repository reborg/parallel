(ns parallel.merge-sort
  (:refer-clojure :exclude [sort])
  (:import
    [java.util.concurrent Callable ForkJoinPool]
    [java.util ArrayList Arrays Comparator]))

(set! *warn-on-reflection* true)
(def pool (delay (ForkJoinPool.)))

(definterface IMergeSort
  (merge [middle])
  (sort []))

(deftype MergeSort [^objects a
                    ^int low
                    ^int high
                    ^ForkJoinPool pool
                    ^int threshold
                    ^Comparator cmp]

  Callable
  (call [this] (.sort this))

  IMergeSort
  (merge [this middle]
    (when (pos? (cmp (aget a (dec middle)) (aget a middle)))
      (let [copy-size (- high low)
            lower-size (- middle low)
            ^objects copy (object-array copy-size)]
        (System/arraycopy a low copy 0 copy-size)
        (loop [i low p 0 q lower-size]
          (when (< i high)
            (if (or (>= q copy-size)
                    (and (< p lower-size)
                         (neg? (cmp (aget copy p) (aget copy q)))))
              (do (aset a i (aget copy p))
                  (recur (inc i) (inc p) q))
              (do (aset a i (aget copy q))
                  (recur (inc i) p (inc q)))))))))
  (sort [this]
    (let [size (- high low)]
      (if (<= size threshold)
        (Arrays/sort a low high cmp)
        (let [middle (+ low (bit-shift-right size 1))
              l (MergeSort. a low middle pool threshold cmp)
              h (MergeSort. a middle high pool threshold cmp)]
          (.invokeAll pool (doto (ArrayList.) (.add l) (.add h)))
          (.merge this middle))))))

(defn sort [threshold cmp ^objects a]
  (let [n (alength a)
        ^ForkJoinPool pool @pool]
    (.join (.submit pool (MergeSort. a 0 n pool threshold cmp)))))
