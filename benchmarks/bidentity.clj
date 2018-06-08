(ns bpmap)

(require '[parallel.xf :as xf])
(require '[criterium.core :refer [bench quick-bench]])

(let [items (range 10000)]
  (quick-bench
    (dorun
      (sequence (map identity) items))))
;; 914.020710 µs

(let [items (range 10000)]
  (quick-bench
    (dorun
      (sequence xf-identity items))))
;; 892.697959 µs

(let [items (range 10000)]
  (quick-bench
    (dorun
      (sequence identity items))))
;; 926.697959 µs

(let [items (range 10000)
      xform (comp (map list) identity)]
  (quick-bench
    (dorun
      (sequence xform items items))))
;; 4.09ms

(let [items (range 10000)]
  (quick-bench
    (dorun
      (sequence xf/identity items items))))
;; 2.67ms
