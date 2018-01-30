## parallel

`parallel` is a library of parallel-enabled (not distributed) Clojure functions. Some are designed to emulate existing functions in the standard library, sometimes as drop-in replacement, sometimes with a very different semantic. If you see a function listed below in your project or if you use transducers, chances are you can speed-up your application using parallel. As with any library claiming to speed-up your code, there are too many variables influencing performances that cannot be tested in isolation: **please keep a benchmarking tool ready and measure each of the changes**.

The library also provides additional transducers (not necessarily for parallel use) and supporting utilities. The functions documented below have been tested and benchmarked and are ready to use. Please report any issue or ideas for improvements, I'll be happy to help.

Current:

| Name                                    | Description
|-----------------------------------------| ---------------------------------------------------
| [`p/fold`](#pfold-pxrf-and-pfolder)     | Transducer-aware `r/fold`.
| [`p/amap`](#pamap)                      | Parallel array transformation.
| [`p/count`](#pcount)                    | Transducer-aware parallel `core/count`.
| [`p/frequencies`](#pfrequencies)        | Parallel `core/frequencies`
| [`p/group-by`](#pgroup-by)              | Parallel `core/group-by`
| [`p/update-vals`](#pupdate-vals)        | Updates values in a map in parallel.
| [`p/external-sort`](#pexternal-sort)    | Memory efficient, file-based, parallel merge-sort.
| [`p/sort`](#psort)                      | Parallel `core/sort`.
| [`p/min` and `p/max`](#pmin-and-pmax)   | Parallel `core/min` and `core/max` functions.
| [`p/interleave`](#pinterleave)          | Transducer-enabled `core/interleave`

In the pipeline:

| Name                                    | Description
|-----------------------------------------| ---------------------------------------------------
| `p/split-by`                            | Splitting transducer based on contiguous elements.
| `p/let`                                 | Parallel local binding

### How to use the library

All functions are available through the `parallel` namespace.  Add the following to your project dependencies:

```clojure
[parallel "0.1"]
```

Require at the REPL with:

```clojure
(require '[parallel :as p])
```

Or in your namespace as:

```clojure
(ns mynamespace
  (:require [parallel :as p]))
```

## API

### `p/fold`, `p/xrf` and `p/folder`

`p/fold` is modeled similar to `clojure.core.reducers/fold` function, the entry point into the Clojure reduce-combine (Java fork-join) parallel computation framework. It can be used with transducers like you would with normal `r/fold`:

```clojure
(def v (vec (range 1000)))
(p/fold + ((comp (map inc) (filter odd?)) +) v)
;; 250000
```

And exactly like with normal `r/fold` this would give you inconsistent results when a stateful transducer like `(drop 1)` is introduced:

```clojure
(distinct (for [i (range 1000)]
  (p/fold + ((comp (map inc) (drop 1) (filter odd?)) +) v)))
;; (249999 249498 249499)
```

This is what `p/xrf` is designed for. `p/xrf` is a wrapping utility that hides the way the transducers are combined with the reducing function. More importantly, it takes care of the potential presence of stateful transducers in the chain (like `drop`, `take`, `partition` and so on).

```clojure
(distinct (for [i (range 1000)]
  (p/fold (p/xrf + (map inc) (drop 1) (filter odd?)) v)))
;; (242240)
```

`p/xrf` makes sure that stateful transducer state is allocated at each chunk instead of each thread (the "chunk" is the portion of the initial collection that is not subject to any further splitting). This is a drastic departure from the semantic of the same transducers when used sequentially on the whole input. The first practical implication is that operations like `take`, `drop`, `partition` etc. are isolated in their own chunk and don't see each other state (for example, `(drop 1)` would remove the first element from each chunk, not just the first element from the whole input). The second consequence is that the result is now dependent (consistently) on the number of chunks.

To enable easier design of parallel algorithms, you can pass `p/fold` a number "n" of desired chunks for the parallel computation (n has to be a power of 2 and it defaults to 32 by default). **Note the difference: with `(r/fold)` the computation is chunk-size driven by "n", the desired chunk size (default to 512). With `(p/fold)` the computation is chunk-number driven by "n" the number of desired chunks to compute in parallel**:

```clojure
(p/fold 4 + (p/xrf + (map inc) (drop 1) (filter odd?)) v)
;; 248496
```

Assuming there are 4 cores available, the example above executes on 4 parallel threads. Let's dissect it chunk by chunk:

* We are asking `(p/fold)` to create 4 chunks of the initial vector "v" of 1000 elements. Each chunk ends up having 250 items.
* The content of each chunk can be expressed by the following ranges (the actual type is a subvec not a range but the content it the same): `(range 0 250)`, `(range 250 500)`, `(range 500 750)`, `(range 750 1000)`
* Transducers transform each chunk (composition reads backward like normal transducers): `(filter odd? (drop 1 (map inc (range 0 250))))`, `(filter odd? (drop 1 (map inc (range 250 500))))`, `(filter odd? (drop 1 (map inc (range 500 750))))`, `(filter odd? (drop 1 (map inc (range 750 1000))))`
* The reducing function "+" is applied on the items on each chunk: 15624, 46624, 77624, 108624
* The combining function is again "+", resulting in the final sums: (+ (+ 15624 46624) (+ 77624 108624)) which is 248496.

It can be tricky for arbitrary collection sizes to see what is the best strategy in terms of chunk size or number. The utility function `p/show-chunks` can be used to predict the splitting for a parallel calculation. `p/fold` parameters can be adjusted accordingly. Here's what happens if you have a vector of 9629 items and you'd like 8 chunks to be created. Some of them will be bigger, other will be smaller:

```clojure
(p/show-chunks (vec (range 9629)) 8)
;; (1203 1204 1203 1204 1203 1204 1204 1204)
```

`p/fold` also allows transducers on hash-maps, not just vectors. A hash-map can be folded with transducers (in parallel) like this:

```clojure
(require '[clojure.core.reducers :refer [monoid]])
(def input (zipmap (range 10000) (range 10000)))

(def output
 (p/fold
  (monoid merge (constantly {}))
  (p/xrf conj
    (filter (fn [[k v]] (even? k)))
    (map (fn [[k v]] [k (inc v)]))
    (map (fn [[k v]] [(str k) v])))
  input))
(output "664")
;; 665
```

The single argument for transducers is a vector pair containing a key and a value. In this case each transducer returns another pair to build another map (but that's not required).

Caveats and known problems:

* Stateful transducers like `dedupe` and `distinct`, that operates correctly at the chunk level, can bring back duplicates once combined back into the final result. Keep that in mind if absolute uniqueness is a requirement, you might need an additional step outside `p/fold` to ensure final elimination of duplicates. I'm thinking what else can be done to avoid the problem in the meanwhile.

### `p/amap`

`p/amap` is a parallel version of `core/amap`. It takes an array of objects and a transformation "f" and it mutates the input to produce the transformed version of the output:

```clojure

(def c (range 2e6))
(defn f [x] (if (zero? (rem x 2)) (* 0.3 x) (Math/sqrt x)))

(let [a (to-array c)] (time (p/amap f a)))
;; "Elapsed time: 34.955138 msecs"

(let [^objects a (to-array c)] (time (amap a idx ret (f (aget a idx)))))
;; "Elapsed time: 53.058256 msecs"
```

`p/amap` uses the fork-join framework to update the array in parallel and it performs better than sequential for non-trivial transformations, otherwise the thread orchestration dominates the computational cost. You can optionally pass in a "threshold" which indicates how small the chunk of computation should be before going sequential, otherwise the number is chosen to be `(/ alength (* 2 ncores))`.

### `p/count`

`p/count` can speed up counting on collections when non-trivial transformations are involved. It takes a composition of transducers and the collection to count. It applies the transducers to coll and produces a count of the resulting elements (in this case 1.2M):

```clojure
(def xform
  (comp
    (filter odd?)
    (map inc)
    (map #(mod % 50))
    (mapcat range)
    (map str)))

(p/count xform (range 100000))
;; 1200000
```

`p/count` is eager, transforming "coll" into a vector if it's not already a foldable collection (vectors, maps or reducers/Cat objects). Use `p/count` only if the transformation are altering the number of elements in the input collection, otherwise `core/count` would likely outperform `p/count` in most situation. `p/count` supports stateful transducers. In this example we are dropping 6250 elements from each of the 32 chunks (32 is the default number of chunks `p/count` operates on, so 32x6250=200k elements will be removed):

```clojure
(def xform
  (comp
    (filter odd?)
    (map inc)
    (map #(mod % 50))
    (mapcat range)
    (map str)
    (drop 6250)))

(p/count xform (range 100000))
;; 1000000
```

See [bcount.clj](https://github.com/reborg/parallel/blob/master/benchmarks/bcount.clj) for additional benchmarks.

### `p/frequencies`

Like `core/frequencies`, but executes in parallel. It takes an optional list of transducers (stateless or stateful) to apply to coll before the frequency is calculated. It does not support nil values. The following is the typical word frequencies example:

```clojure
(def war-and-peace "http://www.gutenberg.org/files/2600/2600-0.txt")
(def book (slurp war-and-peace))
(let [freqs (p/frequencies
              (re-seq #"\S+" book)
              (map #(.toLowerCase ^String %)))]
  (take 5 (sort-by last > freqs)))
;; (["the" 34258] ["and" 21396] ["to" 16500] ["of" 14904] ["a" 10388])

(quick-bench (p/frequencies (re-seq #"\S+" book) (map #(.toLowerCase ^String %)))) ;; 165ms
(quick-bench (frequencies (map #(.toLowerCase ^String %) (re-seq #"\S+" book)))) ;; 394ms
```

### `p/group-by`

`p/group-by` is similar to `clojure.core/group-by`, but the grouping happens in parallel. Here's an example about searching most frequent anagrams in a large text:

```clojure
(require '[clojure.string :as s])

(def war-and-peace
  (s/split (slurp "http://gutenberg.org/files/2600/2600-0.txt") #"\W+"))

(def anagrams
  (p/group-by sort war-and-peace (map #(.toLowerCase ^String %))))

(->> anagrams
  (map (comp distinct second))
  (sort-by count >)
  first)

;; ("stop" "post" "spot" "pots" "tops")
```

`p/group-by` takes an optional list of transducers to apply to the items in coll before generating the groups. It has been used in the example to lower-case each word. Note that differently from `clojure.core/group-by`:

* The order of the items in each value vector can change between runs (this can be a problem or not, depending on your use case).
* It does not support nil values in the input collection.

`p/group-by` is generally 2x-5x faster than `clojure.core/group-by`:

```clojure
(require '[criterium.core :refer [quick-bench]])

;; with transformation (which boosts p/group-by even further)
(quick-bench (group-by sort (map #(.toLowerCase ^String %) war-and-peace)))   ;; 957ms
(quick-bench (p/group-by sort war-and-peace (map #(.toLowerCase ^String %)))) ;; 259ms

;; fair comparison without transformations
(quick-bench (group-by sort war-and-peace))   ;; 936ms
(quick-bench (p/group-by sort war-and-peace)) ;; 239ms
```

A further boost can be achieved by avoiding conversion back to immutable data structures:

```clojure
(quick-bench
  (binding [p/*mutable* true]
    (p/group-by sort war-and-peace (map #(.toLowerCase ^String %))))) ;; 168ms
```

When invoked with `p/*mutable*`, `p/group-by` returns a Java ConcurrentHashMap with ConcurrentLinkedQueue as values. They are both easy to deal with from Clojure.

```clojure
(def anagrams
  (binding [p/*mutable* true]
    (p/group-by sort war-and-peace (map #(.toLowerCase ^String %)))))

(distinct (into [] (.get anagrams (sort "stop"))))
;; ("post" "spot" "stop" "tops" "pots")
```

### `p/update-vals`

`p/'update-vals` updates the values of a map in parallel. With reference to the [`p/group-by`](#pgroup-by) example of the most frequent anagrams, we could apply the step to calculate the distinct words for each key on the map in parallel ("anagrams" is the map resulting from applying `p/group-by` to a large text):

```clojure

(first anagrams)
;; [(\a \d \e \e \h \t) ["heated" "heated" "heated" "heated" "heated" "heated" "heated" "heated"]]

(first (p/update-vals anagrams distinct))
;; [(\a \d \e \e \h \t) ("heated")]
```

Like other functions in the library, `p/update-vals` speed can be improved removing the conversation back into a mutable data structure:

```clojure
(time (dorun (p/update-vals anagrams distinct)))
;; "Elapsed time: 18.462031 msecs"
(time (dorun (binding [p/*mutable* true] (p/update-vals anagrams distinct))))
;; "Elapsed time: 9.908815 msecs"
```

In the context of the previous computation of the most frequent anagrams, we could operate using a combination of mutable `p/sort` and `p/update-vals` and compare it with the previous solution:

```clojure
(import '[java.util Map$Entry])

(defn cmp [^Map$Entry e1 ^Map$Entry e2]
  (> (count (.getValue e1))
     (count (.getValue e2))))

(time (binding [p/*mutable* true]
  (let [a (p/sort cmp (p/update-vals anagrams distinct))]
    (.getValue ^Map$Entry (aget ^objects a 0)))))
;; "Elapsed time: 128.422734 msecs"
;; ("post" "spot" "stop" "tops" "pots")

(time (->> anagrams
  (map (comp distinct second))
  (sort-by count >)
  first))
;; "Elapsed time: 251.277616 msecs"
;; ("post" "spot" "stop" "tops" "pots")
```

The mutable version is roughly 50% faster, but it's verbose and requires type annotations.

### `p/sort`

`p/sort` is a parallel merge-sort implementation that works by splitting the input into smaller chunks which are ordered sequentially below a certain threshold (8192 is the default). `p/sort` offers similar features to `clojure.core/sort` and it's not lazy. The following uses the default comparator `<` to sort a collection of 2M numbers (and by comparison doing the same with `core/sort`):

```clojure
(let [coll (shuffle (range 2e6))] (time (dorun (p/sort coll))))
;; "Elapsed time: 1335.769356 msecs"

(let [coll (shuffle (range 2e6))] (time (dorun (sort coll))))
;; "Elapsed time: 2098.151666 msecs"
```

Or reverse sorting strings:

```clojure
(let [coll (shuffle (map str (range 2e6)))] (time (dorun (p/sort #(compare %2 %1) coll))))
;; "Elapsed time: 1954.57439 msecs"

(let [coll (shuffle (map str (range 2e6)))] (time (dorun (sort #(compare %2 %1) coll))))
;; "Elapsed time: 2540.829781 msecs"
```

`p/sort` is implemented on top of mutable native arrays, converting both input/output into immutable vectors as a default. There are a few ways to speed-up sorting with `p/sort`:

* Vector inputs are preferable than sequences.
* Shave additional milliseconds by using the raw array output, by enclosing `p/sort` in a binding like `(binding [p/*mutable* true] (p/sort coll))`. `p/sort` returns an object array in this case, instead of a vector.
* If you happen to be working natively with arrays, be sure to feed `p/sort` with the native array to avoid conversion.

In order of increasing speed:

```clojure
(require '[criterium.core :refer [quick-bench]])

(let [c (into [] (shuffle (range 2e6)))
      a (to-array c)]
  (quick-bench (p/sort c))
  (quick-bench (binding [p/*mutable* true] (p/sort c)))
  (quick-bench (binding [p/*mutable* true] (p/sort a))))

;; 1185ms
;; 1052ms
;; 46ms
```

As you can see, the conversion into array is responsible for most of the sorting time. If you are lucky to work with arrays, sorting is one order of magnitude faster and more memory efficient.

### `p/external-sort`

`merge-sort` is a well known example of parallelizable sorting algorithm. There was also a time when machines had to use tapes to process large amount of data, loading smaller chunks into main memory. `merge-sort` is also suitable for that. Today we still have big-data and slow external storage such as S3 for which something like a file based merge-sort could still be useful. `p/external-sort` can be used to fetch large amount of data from slow storage, order them by some attribute and consume only the part that is actually needed (for example "find the top most" kind of problems).

A simple `p/external-sort` example is the following:

```clojure
(let [fetchf (fn [id] id)
      v (into [] (reverse (range 10000)))]
  (take 5 (p/external-sort 1000 compare fetchf v)))
;; [0 1 2 3 4]
```

`p/external-sort` accepts a vector "v" of IDs as input. The unique identifiers are used to fetch the whole data object from some remote storage. "fetchf" is the way to tell `p/external-sort` how to retrieve the entire object given a single id (in this example, fetching the id has been simulated by returning the id itself). The IDs are split into chunks not bigger than 1000 items each (512 by default).

Once all data is retrieved for a chunk, data are sorted using the given comparator ("compare" is the default) and the result is stored in a temporary file on disk. 16 files are created in this example, as the number of files needs to be a power of two and `(/ 10000 16) = 625` is the first split that generates chunk less than 1000 in size.

Once all chunk are retrieved, sorted and stored on disk, the result is made available as a lazy sequence. If the lazy sequence is never fully consumed, the temporary files are never loaded in memory all at once. We are taking the first 5 elements in the example, which means that some of the stored files are never loaded into memory.

The degree of parallelism with which "fetchf" is invoked is equal to the number of cores (physical or virtual) available on the running system. If the collection of IDs is a not a vector, it will be converted into one.

### `p/min` and `p/max`

`p/min` and `p/max` are analogous to the corresponding `core/min` and `core/max` functions, but they operate in parallel. They assume a vector of numbers as input (a lazy-sequence would have to be completely scanned anyway) and they allow any combination of transducers (stateless or stateful) to be passed in:

```clojure
(let [c (into [] (shuffle (range 100000)))]
  (p/max c (map dec) (filter odd?)))
;; 99997
```

As other parallel functions, `p/min` and `p/max` perform better on large vectors (> 500k elements). At 1 million elements `p/min` and `p/max` are already 50% faster than their sequential relatives, also depending on the number of available cores.

### `p/interleave`

Like `clojure.core/interleave` in transducer version. Docs wip.

#### misc todo

* [ ] `p/fold` Enable extend to (thread-safe) Java collections
* [ ] `p/fold` Enable extend on Cat objects
* [ ] `p/fold` operates on a group of keys for hash-maps.
* [ ] A foldable reader of some sort for large files.
* [ ] Generative testing?
* [ ] CI

## License

Copyright © 2018 Renzo Borgatti @reborg http://reborg.net
Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
