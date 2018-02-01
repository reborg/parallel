(ns bcount)

(require '[criterium.core :refer [bench quick-bench]])
(require '[parallel.core :as p])

;; core/count

(let [coll (range 100000)] (quick-bench (clojure.core/count (filter odd? (map inc coll))))) ;; 4.74ms
(let [coll (into [] (range 100000))] (quick-bench (clojure.core/count coll))) ;; 8.58ns

;; p/count

(let [coll (range 100000)] (quick-bench (p/count coll (filter odd?) (map inc)))) ;; 3.58ms
;; no transforms, falls back on normal count, with some added timing.
(let [coll (into [] (range 100000))] (quick-bench (p/count coll))) ;; 14.21ns

(def xform
  (comp
    (filter odd?)
    (map inc)
    (map #(mod % 50))
    (mapcat range)
    (map str)))

;; to see some speedup we need non-trivial transforms and larger colls.
(let [coll (into [] (range 1000000))] (quick-bench (p/count xform coll))) ;; 408ms

;; here's the same transform with a sequential transduce
(let [coll (into [] (range 1000000))] (quick-bench (transduce xform (completing (fn [sum _] (inc sum))) 0 coll))) ;; 524ms
