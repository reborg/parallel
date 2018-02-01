(ns groupby)

(require '[parallel.core :as p])
(require '[criterium.core :refer [bench quick-bench]])

(def ^:const magnitude 1e5)
(def ^:const repetition 5)
(def ^:const sqrt (Math/sqrt (* repetition magnitude)))

(def v1 ;;all-keys-no-repeat
  (into [] (range (* repetition magnitude))))

(def v2 ;;many-keys-small-repeat
  (into [] (apply concat (for [i (range repetition)] (shuffle (range magnitude))))))

(def v3 ;;medium-keys-medium-repeat
  (into [] (apply concat (for [i (range sqrt)] (range sqrt)))))

(def v4 ;;small-keys-many-repeat
  (into [] (apply concat (for [i (range magnitude)] (range repetition)))))

;; ************* Normal Group-By **************

(quick-bench (clojure.core/group-by identity v1)) ;; 229ms
(quick-bench (clojure.core/group-by identity v2)) ;; 268ms
(quick-bench (clojure.core/group-by identity v3)) ;; 127ms
(quick-bench (clojure.core/group-by identity v4)) ;; 95ms

;; ************* Parallel Group-By **************

(quick-bench (p/group-by identity v1)) ;; 441ms
(quick-bench (p/group-by identity v2)) ;; 168ms
(quick-bench (p/group-by identity v3)) ;; 29ms
(quick-bench (p/group-by identity v4)) ;; 32ms

;; ************* Parallel Group-By Mutable Result **************

(binding [p/*mutable* true] (quick-bench (p/group-by identity v1))) ;; 21ms
(binding [p/*mutable* true] (quick-bench (p/group-by identity v2))) ;; 48ms
(binding [p/*mutable* true] (quick-bench (p/group-by identity v3))) ;; 13ms
(binding [p/*mutable* true] (quick-bench (p/group-by identity v4))) ;; 18ms
