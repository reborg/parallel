(ns xduce
  (:import [xduce.educe Educe])
  (:require [xduce.educe :as educe]))

(defn xeduction
  [& xforms]
  (Educe. (apply comp (butlast xforms)) (last xforms)))

(defn xequence
  ([xform coll]
     (or (clojure.lang.RT/chunkIteratorSeq
         (educe/create xform (clojure.lang.RT/iter coll)))
       ()))
  ([xform coll & colls]
     (or (clojure.lang.RT/chunkIteratorSeq
         (educe/create
           xform
           (map #(clojure.lang.RT/iter %) (cons coll colls))))
       ())))
