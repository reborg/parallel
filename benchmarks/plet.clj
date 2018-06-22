(ns plet)

(require '[criterium.core :refer [quick-bench]])
(require '[parallel.core :as p] :reload)

(quick-bench (let [a (+ 1 2) b (*  4 3)] (+ a b))) ;; 1.43ns
(quick-bench (p/let [a (+ 1 2) b (*  4 3)] (+ a b))) ;; 15us
