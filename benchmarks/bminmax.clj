(ns bminmax)

(require '[criterium.core :refer [bench]])
(require '[parallel.core :as p] :reload)

(def v10k   (assoc (shuffle (range 10000))    5000 -9))
(def v100k  (assoc (shuffle (range 100000))   50000 -9))
(def v1m    (assoc (shuffle (range 1000000))  500000 -9))

;; core reduce
(let [c v10k]  (bench (reduce min c))) ;; 98.237074 µs
(let [c v100k] (bench (reduce min c))) ;; 1.139608 ms
(let [c v1m]   (bench (reduce min c))) ;; 9.963971 ms

;; core apply (lower than reduce)
(let [c v10k]  (bench (apply min c))) ;; 105.267586 µs
(let [c v1m]   (bench (apply min c))) ;; 8.764973 ms

;; parallel
(let [c v10k]  (bench (p/min c))) ;; 83.043014 µs
(let [c v100k] (bench (p/min c))) ;; 665.367802 µs
(let [c v1m]   (bench (p/min c))) ;; 5.474384 ms

;; experiment, comparable but no xforms, abandoned.
; (defn f-compare
;   ([f v]
;    (f-compare (quot (c/count v) (* 2 ncpu)) f v))
;   ([threshold f v]
;    (when (peek v)
;      (mcombine/map
;        #(reduce f (subvec v %1 %2)) f
;        (c/max threshold 2) (c/count v)))))

; (defn min2 [v] (f-compare c/min v))
; (defn max2 [v] (f-compare c/min v))
; (let [c v10k]  (bench (p/min2 c))) ;; 98ms
; (let [c v1m]   (bench (p/min2 c))) ;; 5.474384 ms

;; parallel xforms
(let [c v10k]  (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 219.782220 µs
(let [c v100k] (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 2.722521 ms
(let [c v1m]   (bench (transduce (comp (map inc) (filter odd?)) min ##Inf c))) ;; 22.701385 ms
(let [c v10k]  (bench (p/min c (map inc) (filter odd?)))) ;; 168.950187 µs
(let [c v100k] (bench (p/min c (map inc) (filter odd?)))) ;; 1.361213 ms
(let [c v1m]   (bench (p/min c (map inc) (filter odd?)))) ;; 12.085497 ms

;; min-index

(require '[criterium.core :refer [bench quick-bench]])
(require '[parallel.core :as p] :reload)

(def v10k   (assoc (shuffle (range 10000))    5000 -9))
(def v100k  (assoc (shuffle (range 100000))   50000 -9))
(def v1m    (assoc (shuffle (range 1000000))  500000 -9))

(let [c v10k]  (bench (nth (reduce-kv (fn [r k v] (if (< (peek r) v) r [k v])) [(peek c)] c) 0))) ;; 158.980496 µs
(let [c v100k] (bench (nth (reduce-kv (fn [r k v] (if (< (peek r) v) r [k v])) [(peek c)] c) 0))) ;; 1.757103 ms
(let [c v1m]   (bench (nth (reduce-kv (fn [r k v] (if (< (peek r) v) r [k v])) [(peek c)] c) 0))) ;; 16.123950 ms

(let [c v10k]  (bench (p/min-index c))) ;; 173 µs
(let [c v100k] (bench (p/min-index c))) ;; 1.671476 ms
(let [c v1m]   (bench (p/min-index c))) ;; 27.136612 ms

;; boils down to:
;; why this:

; (let [v v10k]
;   (quick-bench (let [l (count v)]
;     (loop [i 0 n (peek v) idx (dec l)]
;       (if (< i l)
;         (let [n* (nth v i)]
;           (if (< n n*)
;             (recur (unchecked-inc i) n (int idx))
;             (recur (unchecked-inc i) n* (int i))))
;         [idx n])))))

;; is much slower than this:

; (let [c v10k]
;   (quick-bench (reduce-kv (fn [r k v] (if (< (peek r) v) r [k v])) [(peek c)] c)))
