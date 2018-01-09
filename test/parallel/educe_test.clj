(ns parallel.educe-test
  (:import [clojure.lang RT]
           [java.lang.ref ReferenceQueue WeakReference]
           java.util.concurrent.ConcurrentHashMap)
  (:require [parallel.educe :as educe]
            [clojure.test :refer :all]))

(deftest iterate-single
  (testing "iterate single"
    (let [e (educe/create (map inc) (RT/iter (range 3)))]
      (is (= 1 (.next e)))
      (is (= 2 (.next e)))
      (is (= 3 (.next e)))
      (is (not (.hasNext e))))))

(deftest iterate-multi
  (testing "iterate multi"
    (let [e (educe/create (map vector) (map #(RT/iter %) [(range 2) (range 3) (range 5)]))]
      (is (= [0 0 0] (.next e)))
      (is (= [1 1 1] (.next e)))
      (is (= false (.hasNext e))))))

(deftest iterate-expand
  (testing "iterate expand"
    (let [e (educe/create (comp (map inc) (mapcat range)) (RT/iter (range 3)))]
      (is (= [0 0 1 0 1 2]) (doseq [_ (range 6)] (.next e)))
      (is (= false (.hasNext e))))))

(testing "caching iterator"
  (let [cnt (atom 0)
        coll (map #(do (swap! cnt inc) %) (range 10))
        iter (CachingIterator. (RT/iter coll) (ConcurrentHashMap.) (ReferenceQueue.))]
    (is (= nil (doseq [_ (range 10)] (.next iter))))
    (is (= (.reset iter (RT/iter (map #(do (swap! cnt inc) %) (range 10))))))
    (is (= nil (doseq [_ (range 10)] (.next iter))))
    (is (= 20 @cnt))))

; (require '[xduce.educe :as e] :reload)
; (require '[parallel.educe :as e] :reload)
; (let [it (e/create (comp (map inc) (mapcat range)) (clojure.lang.RT/iter (range 3)))] (while (.hasNext it) (prn (.next it))))
; (let [it (clojure.lang.TransformerIterator/create (comp (map inc) (mapcat range)) (clojure.lang.RT/iter (range 3)))] (while (.hasNext it) (prn (.next it))))
