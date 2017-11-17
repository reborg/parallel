(ns bupdate-vals)

(require '[xduce :as x])
(require '[criterium.core :refer [bench quick-bench]])

(defn large-map [i] (into {} (map vector (range i) (range i))))

(defn update-vals [m f]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))

(defn update-vals-transients [m f]
  (persistent! (reduce-kv (fn [m k v] (assoc! m k (f v))) (transient {}) m)))

;; sanity
(def m (large-map 1e5))
(for [i (range 20)]
  (= (sort (vals (update-vals m inc)))
   (sort (vals (x/update-vals m inc)))))

(let [m (large-map 1e5)] (quick-bench (update-vals m inc))) ;; 22ms
(let [m (large-map 1e5)] (quick-bench (update-vals-transients m inc))) ;; 15ms
(let [m (large-map 1e5)] (binding [x/*mutable* true] (quick-bench (x/update-vals m inc)))) ;; 16ms
(let [m (large-map 1e5)] (binding [x/*mutable* false] (quick-bench (x/update-vals m inc)))) ;; 56ms

(let [m (large-map 1e6)] (quick-bench (update-vals m inc))) ;; 551ms
(let [m (large-map 1e6)] (quick-bench (update-vals-transients m inc))) ;; 241ms
(let [m (large-map 1e6)] (binding [x/*mutable* true] (quick-bench (x/update-vals m inc)))) ;; 215ms
(let [m (large-map 1e6)] (binding [x/*mutable* false] (quick-bench (x/update-vals m inc)))) ;; 1.09secs

;; heavy f calculating pi approx. never going beyond 50 iterations here.
(defn f [n] (->> (range) (filter odd?) (take (rem n 50)) (map / (cycle [1 -1])) (reduce +) (* 4.0)))
(quick-bench (f 50)) ;; 175ns

(let [m (large-map 1e5)] (quick-bench (update-vals m f))) ;; 3.5secs
(let [m (large-map 1e5)] (quick-bench (update-vals-transients m f))) ;; 3.3secs
(let [m (large-map 1e5)] (binding [x/*mutable* false] (quick-bench (x/update-vals m f)))) ;; 1.8secs
(let [m (large-map 1e5)] (binding [x/*mutable* true] (quick-bench (x/update-vals m f)))) ;; 1.6secs
