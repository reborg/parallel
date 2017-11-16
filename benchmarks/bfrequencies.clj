(ns bfrequencies)

(require '[xduce :as x])
(require '[criterium.core :refer [bench quick-bench]])
(require '[clojure.core.reducers :as r])

(import 'java.util.concurrent.atomic.AtomicInteger
        'java.util.concurrent.ConcurrentHashMap
        '[java.util HashMap Collections Map])

(def data1
  (into [] (map hash-map
     (repeat :samplevalue)
     (concat
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))))))

(def data2 (into [] (range 1000)))

(def data3
  (into [] (map hash-map
     (repeat :samplevalue)
     (concat
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))))))

(quick-bench (frequencies data1))
;; 597 ms
(quick-bench (x/frequencies data1))
;; 186 ms

(quick-bench (frequencies (eduction (keep :samplevalue) (map int) data1)))
;; 337 ms
(quick-bench (x/frequencies data1 (keep :samplevalue) (map int)))
;; 128 ms

(quick-bench (frequencies data2))
;; 324 ms
(quick-bench (x/frequencies data2))
;; 0.40 ms

(time (dorun (frequencies data3)))
;; 4320.984379 ms
(time (dorun (x/frequencies data3)))
;; 1980.512017 ms
