(ns parallel.fork-middle
  (:require [clojure.core.reducers :as r])
  (:import [java.util.concurrent Callable ForkJoinPool ForkJoinTask]
           [java.util ArrayList List]))

(set! *warn-on-reflection* true)

(deftype ForkMiddle [^objects a
                     ^int low ^int high ^int radius
                     ^Callable mapf ^Callable f]
  Callable
  (call [this]
    (let [size (- (- high low) (* 2 radius))]
      (if (<= size radius)
        (f mapf low high (inc (quot (- high low) 2)) a)
        (#'r/fjinvoke
          #(let [middle (ForkMiddle. a (+ low radius) (- high radius) radius mapf f)
                 t (.fork (ForkJoinTask/adapt middle))]
             (f mapf low high radius a)
             (.join ^ForkJoinTask t)))))))

(defn submit [mapf f radius ^objects a]
  (let [n (alength a)
        ^ForkJoinPool pool @r/pool]
    (.join (.submit pool (ForkMiddle. a 0 (dec n) radius mapf f)))))

;; Different strategy, similar results.
; (defn fork-tasks
;   "Fork a collection of tasks by recusively
;   splitting into halves."
;   [^List tasks]
;   (let [cnt (.size tasks)]
;     (cond
;       (= 1 cnt) (.call ^Callable (.get tasks 0))
;       (> cnt 1)
;       (let [mid (quot cnt 2)]
;         (#'r/fjinvoke
;           (fn []
;             (let [task (#'r/fjtask #(fork-tasks (.subList tasks mid cnt)))]
;               (#'r/fjfork task)
;               (fork-tasks (.subList tasks 0 mid))
;               (#'r/fjjoin task))))))))

; (defn submit
;   "A forking strategy that chops off chunks
;   at the edges and fork the rest in the middle."
;   [mapf f ^long radius ^objects a]
;   (let [tasks (ArrayList.)]
;     (loop [low 0 high (dec (alength a))]
;       (if (> (- (- high low) (* 2 radius)) radius)
;         (do
;           (.add tasks #(f mapf low high radius a))
;           (recur (+ low radius) (- high radius)))
;         (.add tasks #(f mapf low high (inc (quot (- high low) 2)) a))))
;     (fork-tasks tasks)))
