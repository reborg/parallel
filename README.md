## parallel

`parallel` is a library of parallel-enabled (multi-core CPU) Clojure functions. Some are designed to emulate the related functions
in the standard library (sometimes as a drop-in replacement, sometimes with a different semantic).
The library also provides some new transducers and supporting utilities. It has been tested and benchmarked, but please
file a ticket if you find the next bug.

#### Content (in progress)

Name                  | Type         | Description
-------------------   | ------------ | ---------------------------------------------------
* [x] `p/interleave`  | Transducer   | Like `core/interleave`
* [x] `p/fold`        | Reducers     | Like `r/fold` also supporting stateful transducers
* [x] `p/fold`        | Reducers     | Enable transducers on hash-map folding.
* [ ] `p/fold`        | Reducers     | Enables `r/fold` on (thread-safe) Java collections
* [ ] `p/fold`        | Reducers     | Enables `r/fold` Cat objects
* [ ] `p/fold`        | Reducers     | `p/fold` to operate on a group of keys for hash-maps.
* [ ] `p/merge-sort`  | Function     | Memory efficient parallel merge-sort
* [ ] `p/eduction`    | Transducers  | Alternative iterators for `eduction` (experimental)
* [x] `p/update-vals` | Function     | Updates values in a map in parallel.
* [ ] `p/mapv`        | Function     | Transform a vector in parallel and returns a vector.
* [ ] `p/filterv`     | Function     | Filter a vector in parallel and returns a vector.
* [x] `p/frequencies` | Function     | Like `core/frequencies` but in parallel.

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

To enable easier design of parallel algorithms, you can pass `p/fold` a number "n" of desired chunks for the parallel computation (n has to be a power of 2 and it defaults to 32 by default). Note the difference: with `(r/fold)` the computation is chunk-size driven by "n", the desired chunk size (default to 512). With `(p/fold)` the computation is chunk-number driven by "n" the number of desired chunks to compute in parallel:

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

It can be trickier for arbitrary collection sizes to see what is the best strategy in terms of chunk sizes and number. The utility function `p/show-chunks` can be used to predict the splitting of a parallel calculation, so parameters can be adjusted accordingly. Here's what happens if you have a vector of 9629 items and you'd like 8 chunks to be created. Some of them will be bigger, other will be smaller:

```clojure
(p/show-chunks (vec (range 9629)) 8)
;; (1203 1204 1203 1204 1203 1204 1204 1204)
```

`p/fold` also enables transducers for hash-maps, not just vectors. A hash-map can be folded with transducers (in parallel) like this:

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

As you can see, transducing functions need to accept a single key-value pair argument. In this case they return another pair (because we are building back another map) but that's not required by the library.

#### Caveats and known problems

* Stateful transducers like `dedupe` and `distinct` that operates correctly at the chunk level can bring back duplicates once combined back into the final result.
* Stateful transducers can be used with `p/fold` on hash-maps, but given they are initialized by chunk and a chunk is a key-value pair, they are not currently of much value. I'm investigating better strategies, such as changing the chunk size to operate on a group of keys instead.

### `p/interleave`

### `p/frequencies`

### `p/update-vals`

## License

Copyright © 2017 Renzo Borgatti @reborg http://reborg.net

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
