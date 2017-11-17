(ns bfrequencies)

(require '[xduce :as x])
(require '[criterium.core :refer [bench quick-bench]])
(require '[clojure.core.reducers :as r])

(import 'java.util.concurrent.atomic.AtomicInteger
        'java.util.concurrent.ConcurrentHashMap
        '[java.util HashMap Collections Map])

(def small-overlapping
  (into [] (map hash-map
     (repeat :samplevalue)
     (concat
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))
       (shuffle (range 0. 1e5))))))

(def big-overlapping
  (into [] (map hash-map
     (repeat :samplevalue)
     (concat
       (shuffle (range 6e4 1e5))
       (shuffle (range 6e4 1e5))
       (shuffle (range 6e4 1e5))
       (shuffle (range 6e4 1e5))
       (shuffle (range 6e4 1e5))))))

(def no-overlapping (into [] (range 1000)))

(def bigger-data
  (into [] (map hash-map
     (repeat :samplevalue)
     (concat
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))
       (shuffle (range 0. 7e5))))))



;; small overlapping
(quick-bench (frequencies small-overlapping))
;; 441 ms
(quick-bench (x/frequencies small-overlapping))
;; 190 ms
(binding [x/*mutable* true] (quick-bench (x/frequencies small-overlapping)))
;; 92ms


;; bigger overlapping
(quick-bench (frequencies big-overlapping))
;; 172ms
(quick-bench (x/frequencies big-overlapping))
;; 52ms
(binding [x/*mutable* true] (quick-bench (x/frequencies big-overlapping)))
;; 28ms



;; with xforms

(quick-bench (frequencies (eduction (keep :samplevalue) (map int) small-overlapping)))
;; 238 ms
(quick-bench (x/frequencies small-overlapping (keep :samplevalue) (map int)))
;; 91 ms
(binding [x/*mutable* true] (quick-bench (x/frequencies small-overlapping (keep :samplevalue) (map int))))
;; 50 ms

(quick-bench (frequencies no-overlapping))
;; 335 µs
(quick-bench (x/frequencies no-overlapping))
;; 299 µs

(time (dorun (frequencies bigger-data)))
;; 4320.984379 ms
(time (dorun (x/frequencies bigger-data)))
;; 1980.512017 ms
