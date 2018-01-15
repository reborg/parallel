## parallel

`parallel` is a library of parallel-enabled (not distributed) Clojure functions. Some are designed to emulate already existing functions
in the standard library. Sometimes it is a drop-in replacement, sometimes with a completely different semantic.
The library also provides additional transducers (not necessarily for parallel use) and supporting utilities.

Status: project is public to receive feedback, but not yet on Clojars. When an API is not documented below, consider it still in progress.

#### Content

| Name                                    | Description
|-----------------------------------------| ---------------------------------------------------
| [`p/fold`](#pfold-pxrf-and-pfolder)     | Like `r/fold` also supporting stateful transducers
| [`p/update-vals`](#pupdate-vals)        | Updates values in a map in parallel.
| [`p/interleave`](#pinterleave)          | Like `core/interleave`
| [`p/frequencies`](#pfrequencies)        | Like `core/frequencies`
| [`p/count`](#pcount)                    | Parallel count
| [`p/group-by`](#pgroup-by)              | Parallel `core/group-by`
| [`p/merge-sort`](#pmerge-sort)          | Memory efficient parallel merge-sort
| `p/split-by`                            | Splitting transducer based on contiguous elements.
| `p/mapv`                                | Transform a vector in parallel and returns a vector.
| `p/filterv`                             | Filter a vector in parallel and returns a vector.

#### TODO:

* [ ] `p/fold` Enable extend to (thread-safe) Java collections
* [ ] `p/fold` Enable extend on Cat objects
* [ ] `p/fold` operates on a group of keys for hash-maps.
* [ ] A foldable reader of some sort for large files.
* [ ] All functions benchmarked and documented
* [ ] Clojure 1.7->1.9 compatibility tests
* [ ] Generative testing?
* [ ] Jar on Clojars
* [ ] CI

### How to use the library

All functions are available through the `parallel` namespace.
Add the following to your project dependencies:

```clojure
[parallel "0.1"]
```

Then require at the REPL with:

```clojure
(require '[parallel :as p])
```

Or in your namespace as:

```clojure
(ns mynamespace
  (:require [parallel :as p]))
```

## API Docs

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

* Stateful transducers like `dedupe` and `distinct`, that operates correctly at the chunk level, can bring back duplicates once combined into the final result. Keep that in mind if absolute uniqueness is a requirement, you might need an additional step outside `p/fold` to ensure final elimination of duplicates.
* Stateful transducers can be used with `p/fold` on hash-maps as well, but given that each chunk contains a single key-value pair by default, they are not currently of big value. The library might introduce different strategies in the future to group key-value pairs as sub-maps.

### `p/count`

`p/count` can speed up counting on collections when non-trivial transformations (and large collections) are involved. It takes a composition of transducers and the collection to count. It applies the transducers to coll and produces a count of the resulting elements:

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

`p/count` supports stateful transducers. In this example we are dropping 6250 elements from each of the 32 chunks (32x6250=200000):

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

See [bcount.clj](https://github.com/reborg/parallel/blob/master/benchmarks/bcount.clj) for benchmarks.
`p/count` is eager, transforming "coll" into a vector if it's not already a foldable collection (vectors, maps or reducers/Cat objects).

### `p/interleave`

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

### `p/update-vals`

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

### `p/merge-sort`

`merge-sort` is a well known example of parallelizable sorting algorithm. It was especially useful in the past when machines had to use tapes to process large amount of data, loading smaller chunks on demand into the main memory. It can still be useful today, when the source of the data is some fixed order slow storage such as Amazon S3. `p/merge-sort` could be used to fetch large amount of data from S3, order them by some attribute and consume only the part that is actually needed (for example "find the top most" kind of problems).

A simple `p/merge-sort` example is the following:

```clojure
(let [fetchf (fn [id] id)
      v (into [] (reverse (range 10000)))]
  (take 5 (p/merge-sort 1000 compare fetchf v)))
;; [0 1 2 3 4]
```

`p/merge-sort` accepts a vector "v" of IDs as input. The unique identifiers are used to fetch the whole data object from some remote storage. "fetchf" is the way to tell `p/merge-sort` how to retrieve the entire object given a single id (in this example, fetching the id has been simulated by returning the id itself). The IDs are split into chunks not bigger than 1000 items each (512 by default). Once all data is retrieved for a chunk, data are sorted using the given comparator ("compare" is the default) and the result is stored in a temporary file on disk. Once all chunk are retrieved, sorted and stored on disk, the result is made available as a lazy sequence. If the lazy sequence is never fully consumed, the temporary files are never loaded in memory all at once. We are taking the first 5 elements in the example, which means that some of the stored files are never loaded into memory.

The degree of parallelism with which "fetchf" is invoked is equal to the number of cores (physical or virtual) available on the running system.

## License

Copyright © 2017 Renzo Borgatti @reborg http://reborg.net
Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
