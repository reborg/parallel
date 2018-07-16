(ns bpmap)

(require '[parallel.xf :as xf])
(require '[criterium.core :refer [bench quick-bench]])

(defn pi [n]
  (->> (range)
       (filter odd?)
       (take n)
       (map / (cycle [1 -1]))
       (reduce +)
       (* 4.0)))

(let [items (range 1000000)] (time (dorun (sequence (map inc) items)))) ;; 141ms
(let [items (range 1000000)] (time (dorun (sequence (xf/pmap inc) items)))) ;; 2563ms ok

(let [items (range 400 800)] (time (dorun (sequence (map pi) items)))) ;; 11876ms
(let [items (range 400 800)] (time (dorun (sequence (xf/pmap pi) items)))) ;; 418ms ok ok

(let [items (range 400 800)] (time (transduce (map pi) + items))) ;; 11876ms
(let [items (range 400 800)] (time (transduce (xf/pmap pi) + items))) ;; 1256ms
