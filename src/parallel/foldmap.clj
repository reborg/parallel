(ns parallel.foldmap
  (:require [clojure.core.reducers :as r])
  (:import [clojure.lang
            Get
            PersistentHashMap
            PersistentHashMap$ArrayNode
            PersistentHashMap$BitmapIndexedNode
            PersistentHashMap$HashCollisionNode]
           [java.util.concurrent Callable]
           [java.util ArrayList]))

(set! *warn-on-reflection* true)

(defn- agetter [node] (Get/array node))

(defn- callable [f] (reify Callable (call [this] (f))))

(defn- fold-tasks [^ArrayList tasks combinef]
  (cond
    (.isEmpty tasks) (combinef)
    (== 1 (.size tasks)) (.call ^Callable (.get tasks 0))
    :else (let [t1 (.subList tasks 0 (quot (.size tasks) 2))
                t2 (.subList tasks (quot (.size tasks) 2) (.size tasks))
                forked (#'r/fjfork (#'r/fjtask (callable #(fold-tasks t2 combinef))))]
            (combinef (fold-tasks t1 combinef)
                      (#'r/fjjoin forked)))))

(defprotocol Foldmap
  (fold [m n combinef reducef]))

(extend-protocol Foldmap

  PersistentHashMap
  (fold [m n combinef reducef]
    (#'r/fjinvoke
      (reify
        Callable
        (call [this]
          (let [ret (combinef)
                ret (if (Get/root m) (combinef ret (fold (Get/root m) n combinef reducef)) ret)]
            (if (Get/hasNullValue m)
              (combinef ret (reducef (combinef) nil (Get/nullValue m)))
              ret))))))

  PersistentHashMap$ArrayNode
  (fold [m n combinef reducef]
    (let [tasks (ArrayList.)
          ^"[Lclojure.lang.PersistentHashMap$INode;" array (agetter m)]
      (amap array idx ret
            (let [node (aget array idx)]
              (if (not (nil? node))
                (.add tasks
                      (reify
                        Callable
                        (call [this]
                          (fold node n combinef reducef)))))))
      (fold-tasks tasks combinef)))

  PersistentHashMap$BitmapIndexedNode
  (fold [m n combinef reducef]
    (let [^objects array (agetter m)]
      (Get/kvreduce array reducef (combinef))))

  PersistentHashMap$HashCollisionNode
  (fold [m n combinef reducef]
    (let [^objects array (agetter m)]
      (Get/kvreduce array reducef (combinef)))))
