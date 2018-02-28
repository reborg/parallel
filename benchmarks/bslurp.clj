(ns bslurp)

(require '[criterium.core :refer [bench quick-bench]])
(require '[parallel.core :as p] :reload)

(let [fname "test/words"] (bench (slurp fname))) ; 6.27ms
(let [fname "test/words"] (bench (p/slurp fname))) ; 2.98ms

(let [fname "/Users/reborg/prj/my/pwc/test/war-and-peace.txt"] (bench (slurp fname))) ; 8.063541 ms
(let [fname "/Users/reborg/prj/my/pwc/test/war-and-peace.txt"] (bench (p/slurp fname))) ; 8.42ms

(let [fname "project.clj"] (bench (slurp fname))) ; 23.22 µs
(let [fname "project.clj"] (bench (p/slurp fname))) ; 183.517530 µs
