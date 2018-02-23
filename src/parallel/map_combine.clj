(ns parallel.map-combine
  (:refer-clojure :exclude [map])
  (:require [clojure.core.reducers :as r])
  (:import [java.util.concurrent Callable ForkJoinPool]))

(set! *warn-on-reflection* true)

(deftype MapCombine [^int low ^int high ^int threshold
                     ^Callable mapf ^Callable combinef]
  Callable
  (call [this]
    (let [size (- high low)]
      (if (<= size threshold)
        (mapf low high)
        (let [middle (+ low (bit-shift-right size 1))
              l (MapCombine. low middle threshold mapf combinef)
              h (MapCombine. middle high threshold mapf combinef)]
          (let [fc (fn [^Callable child] #(.call child))]
            (#'r/fjinvoke
              #(let [f1 (fc l)
                     t2 (#'r/fjtask (fc h))]
                 (#'r/fjfork t2)
                 (combinef (f1) (#'r/fjjoin t2))))))))))

(defn map [mapf combinef threshold n]
  (let [^ForkJoinPool pool @r/pool]
    (.join (.submit pool (MapCombine. 0 n threshold mapf combinef)))))
