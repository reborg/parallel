(ns barmap)

(require '[criterium.core :refer [quick-benchmark quick-bench]])
(defmacro b [expr] `(* 1000. (first (:mean (quick-benchmark ~expr {}))))) ;; mssecs
(require '[parallel.core :as p] :reload)

(defn armap
  "Fair sequential comparison"
  [f ^objects a]
  (loop [i 0]
    (when (< i (quot (alength a) 2))
      (let [tmp (f (aget a i))
            j (- (alength a) i 1)]
        (aset a i (f (aget a j)))
        (aset a j tmp))
      (recur (unchecked-inc i)))))

(def coll (range 1e6))

;; sequential identity
(let [c (object-array coll)] (b (armap identity c)))    ; 1.28
(let [c (object-array coll)] (b (p/armap identity c)))  ; 11.14 (10x slow)

;; reverse-complement example
(defn random-dna [n] (repeatedly n #(rand-nth [\a \c \g \t])))
(def compl {\a \t \t \a \c \g \g \c})
(let [c (random-dna 1e6)
      a1 (object-array c)
      a2 (object-array c)]
  [(b (armap compl a1))
   (b (p/armap compl a2))])

;; [70.55341358333335 39.12026016666667] (~1.5x faster)

;; even more demanding f
(defn pi [n] (->> (range) (filter odd?) (take n) (map / (cycle [1 -1])) (reduce +) (* 4.0)))

(let [ps (shuffle (range 400 800))
      a1 (object-array ps)
      a2 (object-array ps)]
  (quick-bench (armap pi a1))   ; 1.246923 ms
  (quick-bench (p/armap pi a2)) ; 0.866139 ms
  )
