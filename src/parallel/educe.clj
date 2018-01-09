(ns parallel.educe
  (:import
    [java.lang.ref ReferenceQueue Reference WeakReference]
    [java.util.concurrent ConcurrentHashMap]
    [java.util Map Map$Entry Queue LinkedList Iterator NoSuchElementException]
    [clojure.lang RT ArraySeq]))

(set! *warn-on-reflection* true)

(defonce NONE (Object.))

(definterface Buffer
  (add [o])
  (remove [])
  (get [])
  (isEmpty []))

(definterface Reloadable
  (reset [newiter]))

(deftype Many [^Queue vals]
  Buffer
  (add [this o] (.add vals o) this)
  (remove [this] (.remove vals))
  (get [this] vals)
  (isEmpty [this] (.isEmpty vals))
  (toString [this] (str "Many: " (.toString vals))))

(deftype Single [^:volatile-mutable ^Object val]
  Buffer
  (add [this o]
    (if (identical? val NONE)
      (do (set! val o)
          this)
      (Many. (doto (LinkedList.) (.add val) (.add o)))))
  (remove [this]
    (when (identical? val NONE)
      (throw (IllegalStateException. "Removing object from empty buffer")))
    (let [ret val]
      (set! val NONE)
      ; (println "I. Single::remove returning val" ret)
      ret))
  (get [this]
    (do
      ; (println "II. Single::get val is" val)
      val))
  (isEmpty [this] (identical? val NONE))
  (toString [this] (str "Single: " val)))

(defn- clear [^ConcurrentHashMap chm ^ReferenceQueue rq]
  (when (.poll rq)
    (while (.poll rq))
    (doseq [^Map$Entry e (.entrySet chm)]
      (let [^Reference val (.getValue e)]
        (when (and (not (nil? val)) (nil? (.get val)))
          (.remove chm (.getKey e) val))))))

(defn- get-clear [^Object k ^Object e ^ConcurrentHashMap chm ^ReferenceQueue rq]
  (or
    (let [^Reference hit (.get chm k)]
      (when hit (println "cache hit for k" k "is" (.get hit)))
      hit)
    (do
      (clear chm rq)
      (println "cache miss for" k)
      (.putIfAbsent chm k (WeakReference. e rq)))))

(defn- cached [e ^ConcurrentHashMap chm ^ReferenceQueue rq]
  (let  [k (hash e)]
    (if-let [^Reference wref (get-clear k e chm rq)]
      (if-let [fromcache (.get wref)]
        fromcache
        (do
          ; (println "ref died, start over")
          (.remove chm k wref)
          (cached e chm rq)))
      e)))

(deftype CachingIterator [^Iterator ^:volatile-mutable iter
                          ^ConcurrentHashMap chm
                          ^ReferenceQueue rq]
  Reloadable
  (reset [this newiter]
    ; (println "a. CachingIterator::reset new iter" (type newiter))
    (set! iter newiter))
  Iterator
  (hasNext [this]
    (let [hasnext (.hasNext iter)]
      ; (println "b. CachingIterator::hasNext is" hasnext)
      hasnext))
  (next [this]
    (let [n (.next iter)]
      ; (println "c. CachingIterator::next next is" n)
      (cached n chm rq)))
  (remove [this] (.remove iter)))

(deftype Empty []
  Buffer
  (add [this o] (Single. o))
  (remove [this] (throw (IllegalStateException. "Removing object from empty buffer")))
  (get [this] this)
  (isEmpty [this] true)
  (toString [this] "Empty"))

(deftype TransformerIterator [^clojure.lang.IFn xf
                              ^Iterator sourceIter
                              multi
                              buffer
                              ^:volatile-mutable next
                              ^:volatile-mutable completed]
  Reloadable
  (reset [this newiter]
    ; (println "0. TransformerIterator::reset new iter. Buffer contains" (.get @buffer))
    (.reset ^Reloadable sourceIter newiter)
    (set! completed false)
    (vreset! buffer (Empty.))
    (set! next NONE))
  Iterator
  (hasNext [this]
    (if (identical? next NONE)
      (do
        ; (println "1. TransformerIterator::hasNext next is NONE")
        (while (and (identical? next NONE) (not completed))
          (if (.isEmpty ^Buffer @buffer)
            (if (.hasNext sourceIter)
              (let [n (.next sourceIter)
                    ; _ (println "2. TransformerIterator::hasNext next is" n)
                    iter (if multi
                           (.applyTo xf (RT/cons nil n))
                           (.invoke xf nil n))]
                (when (RT/isReduced iter)
                  ; (println "3. TransformerIterator::hasNext iter is reduced. Completed set to true.")
                  (.invoke xf nil)
                  (set! completed true)))
              (do
                (.invoke xf nil)
                (set! completed true)))
            (do
              ; (println "3. TransformerIterator::hasNext, buffer contains" (.get @buffer))
              (set! next (.remove ^Buffer @buffer)))))
        (not completed))
      true))
  (next [this]
      ; (println "4a. TransformerIterator::next this is" this)
    (let [hasnext (.hasNext this)]
      ; (println "4b. TransformerIterator::next hasnext?" hasnext "next currently" next)
      (if hasnext
        (let [ret next]
          (set! next NONE)
          ret)
        (throw (NoSuchElementException.)))))
  (remove [this] (throw (UnsupportedOperationException.))))

(deftype MultiIterator [^"[Ljava.util.Iterator;" iters]
  Iterator
  (hasNext [this]
    (loop [idx 0]
      (if (== (alength iters) idx)
        true
        (if (.hasNext ^Iterator (aget iters idx))
          (recur (inc idx))
          false))))
  (next [this]
    (let [nexts (make-array Object (alength iters))]
      (loop [idx 0]
        (when (< idx (alength iters))
          (aset ^"[Ljava.lang.Object;" nexts idx (.next ^Iterator (aget iters idx)))
          (recur (inc idx))))
      (ArraySeq/create nexts)))
  (remove [this] (throw (UnsupportedOperationException.))))

(defn create [xform iter & [buffer]]
  (let [buffer (or buffer (volatile! (Empty.)))
        buff-fn (fn ([]) ([acc] acc) ([acc o] (vreset! buffer (.add ^Buffer @buffer o)) acc))]
    (if (instance? Iterator iter)
      (CachingIterator. (TransformerIterator. (xform buff-fn) iter false buffer NONE false) (ConcurrentHashMap.) (ReferenceQueue.))
      (TransformerIterator. (xform buff-fn) (CachingIterator. (MultiIterator. (into-array Iterator iter)) (ConcurrentHashMap.) (ReferenceQueue.)) true buffer NONE false))))

(deftype Educe [^Iterator ^:unsynchronized-mutable iter xform coll]
   Iterable
   (iterator [_]
     ; (create xform (RT/iter coll))
     (if (nil? iter)
       (do
         ; (println "No iter, new chain.")
         (set! iter (create xform (RT/iter coll))))
       (do
         ; (println "I. Existing chain with iter" iter)
         (.reset ^Reloadable iter (RT/iter coll))
         iter)))

   clojure.lang.IReduceInit
   (reduce [_ f init] (transduce xform (completing f) init coll))

   clojure.lang.Sequential)

(defmethod print-method Educe [c, ^java.io.Writer w]
  (if *print-readably*
    (#'clojure.core/print-object c w)
    (do
      (#'clojure.core/print-sequential "(" #'clojure.core/pr-on " " ")" c w))))
