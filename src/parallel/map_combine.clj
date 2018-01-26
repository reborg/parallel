(ns parallel.map-combine
  (:refer-clojure :exclude [map])
  (:require [clojure.core.reducers :as r])
  (:import [java.util.concurrent Callable ForkJoinPool]))

(set! *warn-on-reflection* true)

(deftype MapCombine [^objects a
                     ^int low ^int high ^int threshold
                     ^Callable mapf ^Callable combinef]
  Callable
  (call [this]
    (let [size (- high low)]
      (if (<= size threshold)
        (mapf low high a)
        (let [middle (+ low (bit-shift-right size 1))
              l (MapCombine. a low middle threshold mapf combinef)
              h (MapCombine. a middle high threshold mapf combinef)]
          (let [fc (fn [^Callable child] #(.call child))]
            (#'r/fjinvoke
              #(let [f1 (fc l)
                     t2 (#'r/fjtask (fc h))]
                 (#'r/fjfork t2)
                 (combinef (f1) (#'r/fjjoin t2))))))))))

(defn map [mapf combinef threshold ^objects a]
  (let [n (alength a)
        ^ForkJoinPool pool @r/pool]
    (.join (.submit pool (MapCombine. a 0 n threshold mapf combinef)))))
