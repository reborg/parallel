(ns parallel.foldmap
  (:require [clojure.core.reducers :as r])
  (:import [clojure.lang RT Get
            PersistentHashMap
            PersistentHashMap$INode
            PersistentHashMap$ArrayNode
            PersistentHashMap$BitmapIndexedNode
            PersistentHashMap$HashCollisionNode]
           [java.util.concurrent Callable]
           [java.util ArrayList List]))

(set! *warn-on-reflection* false)

(defn- agetter
  "Trickiness. This needs to be an indirected call.
  It prevents Clojure from inlining Get/array implementation
  into the generated function class. The generated parallel.foldmap
  package class doesn't have protected access to clojure.lang.
  Will always throw a reflection warning."
  [node] (Get/array node))

(set! *warn-on-reflection* true)

(defn- fold-tasks [^List tasks combinef]
  (cond
    (.isEmpty tasks) (combinef)
    (== 1 (.size tasks)) (.call ^Callable (.get tasks 0))
    :else (let [t1 (.subList tasks 0 (quot (.size tasks) 2))
                t2 (.subList tasks (quot (.size tasks) 2) (.size tasks))
                forked (#'r/fjfork (#'r/fjtask #(fold-tasks t2 combinef)))]
            (combinef (fold-tasks t1 combinef)
                      (#'r/fjjoin forked)))))

(defn- compose
  "As a consequence, reducef cannot be a vector."
  [xrf]
  (if (vector? xrf)
    ((last xrf) (first xrf))
    xrf))

(defprotocol Foldmap
  (fold [m n combinef reducef])
  (kvreduce [node f init]))

(extend-protocol Foldmap

  (Class/forName "[Ljava.lang.Object;")
  (fold [m n combinef reducef]
    (throw (RuntimeException. "Not implemented")))
  (kvreduce [node f init]
    ;; workaround type hints are lost [CLJ-1381]
    (let [^"[Ljava.lang.Object;" node node ^Object init init]
      (loop [idx 0 res init]
        (if (or (RT/isReduced res) (>= idx (alength ^"[Ljava.lang.Object;" node)))
          res
          (let [idx+1 (unchecked-inc idx)
                idx+2 (unchecked-add idx 2)]
            (if (nil? (aget node idx))
              (let [node (aget node idx+1)]
                (if (nil? node)
                  (recur idx+2 res)
                  (recur idx+2 (kvreduce node f res))))
              (recur idx+2 (f res [(aget node idx) (aget node idx+1)]))))))))

  PersistentHashMap
  (fold [m n combinef reducef]
    (#'r/fjinvoke
      #(let [ret (combinef)
             ret (if (Get/root m) (combinef ret (fold (Get/root m) n combinef reducef)) ret)]
         (if (Get/hasNullValue m)
           (combinef ret (reducef (combinef) nil (Get/nullValue m)))
           ret))))
  (kvreduce [node f init]
    (throw (RuntimeException. "Not implemented")))

  PersistentHashMap$ArrayNode
  (fold [m n combinef reducef]
    (let [tasks (ArrayList.)
          ^"[Lclojure.lang.PersistentHashMap$INode;" array (agetter m)]
      (dotimes [idx (alength array)]
        (let [node (aget array idx)]
          (if (not (nil? node))
            (.add tasks #(fold node n combinef reducef)))))
      (fold-tasks tasks combinef)))
  (kvreduce [node f init]
    (let [^"[Ljava.lang.Object;" node node
          ^"[Lclojure.lang.PersistentHashMap$INode;" array (agetter node)]
      (loop [idx 0 res init]
        (if (or (RT/isReduced res) (>= idx (alength node)))
          res
          (if (nil? (aget array idx))
            (recur (unchecked-inc idx) res)
            (recur (unchecked-inc idx) (kvreduce node f res)))))))

  PersistentHashMap$BitmapIndexedNode
  (fold [m n combinef reducef]
    (let [^objects array (agetter m)]
      (kvreduce array (compose reducef) (combinef))))
  (kvreduce [node f init]
    (let [^"[Lclojure.lang.PersistentHashMap$INode;" array (agetter node)]
      (kvreduce array f init)))

  PersistentHashMap$HashCollisionNode
  (fold [m n combinef reducef]
    (let [^objects array (agetter m)]
      (kvreduce array (compose reducef) (combinef))))
  (kvreduce [node f init]
    (let [^"[Lclojure.lang.PersistentHashMap$INode;" array (agetter node)]
      (kvreduce array f init))))
