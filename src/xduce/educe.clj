(ns xduce.educe
  (:import
    [java.lang.ref ReferenceQueue WeakReference]
    java.util.concurrent.ConcurrentHashMap
    [java.util Queue LinkedList Iterator NoSuchElementException]
    [clojure.lang RT ArraySeq]))

(set! *warn-on-reflection* true)

(defonce NONE (Object.))

(definterface Buffer
  (add [o])
  (remove [])
  (isEmpty []))

(deftype Many [^Queue vals]
  Buffer
  (add [this o] (.add vals o) this)
  (remove [this] (.remove vals))
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
      ret))
  (isEmpty [this] (identical? val NONE))
  (toString [this] (str "Single: " val)))

(deftype Empty []
  Buffer
  (add [this o] (Single. o))
  (remove [this] (throw (IllegalStateException. "Removing object from empty buffer")))
  (isEmpty [this] true)
  (toString [this] "Empty"))

(deftype TransformerIterator [^clojure.lang.IFn xf
                              ^Iterator sourceIter
                              multi
                              buffer
                              ^:volatile-mutable next
                              ^:volatile-mutable completed]
  Iterator
  (hasNext [this]
    (if (identical? next NONE)
      (do
        (while (and (identical? next NONE) (not completed))
          (if (.isEmpty ^Buffer @buffer)
            (if (.hasNext sourceIter)
              (let [iter (if multi
                           (.applyTo xf (RT/cons nil (.next sourceIter)))
                           (.invoke xf nil (.next sourceIter)))]
                (when (RT/isReduced iter)
                  (.invoke xf nil)
                  (set! completed true)))
              (do
                (.invoke xf nil)
                (set! completed true)))
            (set! next (.remove ^Buffer @buffer))))
        (not completed))
      true))
  (next [this]
    (if (.hasNext this)
      (let [ret next]
        (set! next NONE)
        ret)
      (throw (NoSuchElementException.))))
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

(defn- create-single [xform iterator buffer buff-fn]
  (TransformerIterator. (xform buff-fn) iterator false buffer NONE false))

(defn- create-multi [xform sources buffer buff-fn]
  (let [iters (into-array Iterator sources)]
    (TransformerIterator. (xform buff-fn) (MultiIterator. iters) true buffer NONE false)))

(defn create [xform iter]
  (let [buffer (volatile! (Empty.))
        buff-fn (fn ([]) ([acc] acc) ([acc o] (vreset! buffer (.add ^Buffer @buffer o)) acc))]
    (if (instance? Iterator iter)
      (create-single xform iter buffer buff-fn)
      (create-multi xform iter buffer buff-fn))))

(deftype Educe [xform coll]
   Iterable
   (iterator [_] (create xform (RT/iter coll)))

   clojure.lang.IReduceInit
   (reduce [_ f init] (transduce xform (completing f) init coll))

   clojure.lang.Sequential)

(defmethod print-method Educe [c, ^java.io.Writer w]
  (if *print-readably*
    (do
      (#'clojure.core/print-sequential "(" #'clojure.core/pr-on " " ")" c w))
    (#'clojure.core/print-object c w)))
