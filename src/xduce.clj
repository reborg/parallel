(ns xduce
  (:refer-clojure :exclude [eduction sequence])
  (:require [xduce.educe :as educe])
  (:import [xduce.educe Educe]))

(defn eduction
  [& xforms]
  (Educe. nil (apply comp (butlast xforms)) (last xforms)))

(defn sequence
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
