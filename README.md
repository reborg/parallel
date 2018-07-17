## parallel

`parallel` is a library of parallel-enabled (not distributed) Clojure functions. Some are designed to emulate existing functions in the standard library, sometimes as drop-in replacement, sometimes with a very different semantic. If you see a function listed below in your project or if you use transducers, chances are you can speed-up your application using parallel. As with any library claiming to speed-up your code, there are too many variables influencing performances that cannot be tested in isolation: **please keep a benchmarking tool ready and measure each of the changes**.

The library also provides additional transducers (not necessarily for parallel use) and supporting utilities. The functions documented below have been tested and benchmarked and are ready to use. Please report any issue or ideas for improvements, I'll be happy to help.

Current:

| Name                                    | Description
|-----------------------------------------| ---------------------------------------------------
| [`p/let`](#plet)                        | Parallel `let` bindings.
| [`p/slurp`](#pslurp)                    | Parallel slurping files.
| [`p/count`](#pcount)                    | Transducer-aware parallel `core/count`.
| [`p/frequencies`](#pfrequencies)        | Parallel `core/frequencies`
| [`p/group-by`](#pgroup-by)              | Parallel `core/group-by`
| [`p/update-vals`](#pupdate-vals)        | Updates values in a map in parallel.
| [`p/external-sort`](#pexternal-sort)    | Memory efficient, file-based, parallel merge-sort.
| [`p/sort`](#psort)                      | Parallel `core/sort`.
| [`p/fold`](#pfold-pxrf-and-pfolder)     | Transducer-aware `r/fold`.
| [`p/min` and `p/max`](#pmin-and-pmax)   | Parallel `core/min` and `core/max` functions.
| [`p/distinct`](#pdistinct)   					  | Parallel version of `core/distinct`
| [`p/amap`](#pamap)                      | Parallel array transformation.
| [`p/armap`](#parmap)                    | Parallel array reversal with transformation.
| [`xf/interleave`](#xfinterleave)        | Like `core/interleave`, transducer version.
| [`xf/pmap`](#xfpmap)                    | Like `core/pmap`, transducer version.
| [`xf/identity`](#xfidentity)            | Alternative identity transducer to `core/identity`

In the pipeline:

| Name                                    | Description
|-----------------------------------------| ---------------------------------------------------
| `p/split-by`                            | Splitting transducer based on contiguous elements.
| `p/or` `p/and`                          | Conditions in parallel

### How to use the library

All functions are available through the `parallel.core` namespace. Pure transducers are in `parallel.xf`.  Add the following to your project dependencies:

```clojure
[parallel "0.6"]
```

Require at the REPL with:

```clojure
(require '[parallel.core :as p]
         '[parallel.xf :as xf])
```

Or in your namespace as:

```clojure
(ns mynamespace
  (:require [parallel.core :as p]
            [parallel.xf :as xf]))
```

## API Docs

### `p/let`

`p/let` works like `clojure.core/let` but evaluates its binding expressions in parallel:

```clj
(time
  (p/let [a (Thread/sleep 1000)
          b (Thread/sleep 1000)
          c (Thread/sleep 1000)]
    (= a b c)))
;; "Elapsed time: 1002.519823 msecs"
```

Don't use `p/let` if:

* The expressions have dependencies. `p/let` cannot resolve cross references between expressions and will throw exception.
* The expressions are trivial. In this case the thread orchestration outweighs the benefits of executing in parallel. Good expressions to parallelize are for example independent networked API calls, file system calls or other non trivial computations.

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

### `p/min` and `p/max`

`p/min` and `p/max` find the minimum or maximum in a vector of numbers in parallel (the input collection is converted into a vector if it's not already):

```clojure
(let [c (shuffle (conj (range 100000) -9))]
  (p/min c))
;; -9
```

They also allow any combination of transducers (stateless or stateful) to be passed in as arguments:

```clojure
(let [c (into [] (range 100000))]
  (p/min c
    (map dec)
    (drop 20)
    (partition-all 30)
    (map last)
    (filter odd?))) ;; 3173
```

`p/min` and `p/max` outperform sequential `core/min` and `core/max` starting at 10k items and up (depending on hardware configuration). For a 4 cores machine, the speed increase is roughly 50%:

```clojure
(require '[criterium.core :refer [bench]])
(require '[parallel.core :as p])

(def 1M (shuffle (range 1000000)))

(bench (reduce min 1M)) ;; 9.963971 ms
(bench (p/min 1M))      ;; 5.474384 ms

(bench (transduce (comp (map inc) (filter odd?)) min ##Inf 1M)) ;; 22.701385 ms
(bench (p/min 1M (map inc) (filter odd?)))                      ;; 12.085497 ms
```

### `p/distinct`

`p/distinct` returns a sequence of the distinct items in "coll":

```clojure
(let [c (apply concat (repeat 20 (range 100)))]
  (take 10 (p/distinct c)))
;; (0 1 2 3 4 5 6 7 8 9)
```

The sequence is not-lazy and can return in any order. We can see this by supplying a transducer list (without using `comp`) to change from integers to keywords:

```clojure
(let [c (apply concat (repeat 20 (range 100)))]
  (take 10 (p/distinct c (map str) (map keyword))))
;; (:59 :16 :39 :47 :28 :58 :36 :15 :25 :18)
```

`p/distinct` does not support `nil`, which needs to be removed (you can pass `(remove nil?)` as a transducer to the argument list). Performance of `p/distinct` are quite good on both small and large collections:

```clojure
(require '[criterium.core :refer [quick-bench]])

(let [small (apply concat (repeat 20 (range 100)))
      large (apply concat (repeat 200 (range 10000)))]
  (quick-bench (p/distinct small))
  (quick-bench (p/distinct large)))
;; Execution time mean : 160.949448 µs
;; Execution time mean : 77.772233 ms

(let [small (apply concat (repeat 20 (range 100)))
      large (apply concat (repeat 200 (range 10000)))]
  (quick-bench (doall (distinct small)))
  (quick-bench (doall (distinct large))))
;; Execution time mean : 565.503835 µs
;; Execution time mean : 862.702828 ms
```

You can additionally increase `p/distinct` speed by using a vector input and forcing mutable output (in this case `p/distinct` returns an `java.util.Set` interface):


```clojure
(let [large (into [] (apply concat (repeat 200 (range 10000))))]
  (quick-bench (binding [p/*mutable* true] (p/distinct large))))
;; Execution time mean : 37.703288 ms
```

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

### `p/armap`

`p/armap` is similar to `p/amap` but it also inverts the array. It takes an array of objects and a transformation "f" and it mutates the input to produce the transformed-reverse version of the output.

```clojure
(let [a (object-array [0 9 8 2 0 9 2 2 90 1 2])]
  (p/armap (comp keyword str) a)
  (into [] a))
;; [:2 :1 :90 :2 :2 :9 :0 :2 :8]
```

`p/armap` performs better than sequential for non-trivial transformations, otherwise the thread orchestration dominates the computational cost. Here's for example a reverse-complement of some random DNA strand:

```clojure
(require '[criterium.core :refer [quick-bench]])

(defn random-dna [n] (repeatedly n #(rand-nth [\a \c \g \t])))
(def compl {\a \t \t \a \c \g \g \c})

(defn armap
  "A fair sequential comparison"
  [f ^objects a]
  (loop [i 0]
    (when (< i (quot (alength a) 2))
      (let [tmp (f (aget a i))
            j (- (alength a) i 1)]
        (aset a i (f (aget a j)))
        (aset a j tmp))
      (recur (unchecked-inc i)))))

(let [a (to-array (random-dna 1e6))]
  (quick-bench (p/armap compl a)))
;; "Elapsed time: 39.195143 msecs"

(let [a (to-array (random-dna 1e6))]
  (quick-bench (armap compl a)))
;; "Elapsed time: 70.286622 msecs"
```

You can optionally pass in a "threshold" which indicates how small the chunk of computation should be before going sequential, otherwise the number is chosen to be `(/ alength (* 2 ncores))`.

### `p/slurp`

`p/slurp` loads the content of a file in parallel. Compared to `core/slurp`, it only supports local files (no URLs or other input streams):

```clojure
(import 'java.io.File)
(take 10 (.split (p/slurp (File. "test/words")) "\n"))
;; ("A" "a" "aa" "aal" "aalii" "aam" "Aani" "aardvark" "aardwolf" "Aaron")
```

`p/slurp` offers a way to interpret the loaded byte array differently from a string, for example to load an entry from a zipped file:

```clojure
(import '[java.io File ByteArrayInputStream]
        '[java.util.zip ZipFile ZipInputStream])

(defn filenames-in-zip [bytes]
  (let [z (ZipInputStream. (ByteArrayInputStream. bytes))]
    (.getName (.getNextEntry z))))

(p/slurp (File. "target/parallel-0.5.jar") filenames-in-zip)
;; "META-INF/MANIFEST.MF"
```

When `*mutable*` is set to `true` the transformation step is skipped altogether and the raw byte array is returned:

```clojure
(import 'java.io.File)
(binding [p/*mutable* true] (p/slurp (File. "test/words")))
;; #object["[B" 0x705709a4 "[B@705709a4"]
```

`p/slurp` performs better than `core/slurp` on large files (> 500K). Here's for example a comparison benchmark to load a 2.4MB file:

```clojure
(import 'java.io.File)
(let [fname "test/words" file (File. fname)] (bench (slurp file))) ; 8.84ms
(let [fname "test/words" file (File. fname)] (bench (p/slurp file))) ; 2.87ms
```

### `xf/interleave`

Like `clojure.core/interleave` but in transducer version. When `xf/interleave` is instantiated, it takes a "filler" collection. The items from the collection are used to interleave the others items coming from the main transducing sequence:

```clojure
(sequence
  (comp
    (map inc)
    (xf/interleave [100 101 102 103 104 105])
    (filter odd?)
    (map str))
  [3 6 9 12 15 18 21 24 37 30])
;; ("7" "101" "13" "103" "19" "105")
```

The main transducing process runs until there are items in the filler sequence (those starting at 100 in the example). You can provide an infinite sequence to be sure all results are interleaved:

```clojure
(sequence
  (comp
    (map inc)
    (xf/interleave (range))
    (filter odd?)
    (map str))
  [3 6 9 12 15 18 21 24 37 30])
;; ("7" "1" "13" "3" "19" "5" "25" "7" "31" "9")
```

### `xf/pmap`

`xf/pmap` is a transducer version of `core/pmap`. When added to a transducer chain, it works like `core/map` transducer applying the function "f" to all the items passing through the transducer. Different from `core/map`, `xf/pmap` processes items in parallel up to 32 simultaneously (with physical parallelism equal to the number of available cores):

```clojure
(defn heavyf [x] (Thread/sleep 1000) (inc x))

(time (transduce (comp (map heavyf) (filter odd?)) + (range 10)))
;; 10025ms
(time (transduce (comp (xf/pmap heavyf) (filter odd?)) + (range 10)))
;; 1006ms
```

`xf/pmap` has similar limitations to `core/pmap`. It works great when "f" is non trivial and performance of "f" applied to the input are uniform. If one `(f item)` takes much more than the others, the current 32-chunk is kept busy with parallelism=1 before moving to the next chunk, wasting resources.

### `xf/identity`

`xf/identity` works similarly to `(map identity)` or just `identity` as identity transducer:


```clojure
(sequence (map identity) (range 10))
(sequence clojure.core/identity (range 10))
(sequence xf/identity (range 10))
;; All printing (0 1 2 3 4 5 6 7 8 9)
```

The identity transducer works as a placeholder for those cases in which a transformation is not requested, for example:

```clojure
(def config false)

(defn build-massive-xform []
  (when config
    (comp (map inc) (filter odd?))))

(sequence (or (build-massive-xform) identity) (range 5))
;; (0 1 2 3 4)
```

`core/identity` works fine as a transducer in most cases, except when it comes to multiple inputs, for which it requires a definition of what "identity" means. We could for example agree that if you want to use `core/identity` with multiple inputs you need to use it in pair with another transducer, for example `(map list)`:

```clojure
(sequence (or (build-massive-xform) identity) (range 5) (range 5))
;; Throws exception

(sequence (or (build-massive-xform) (comp (map list) identity)) (range 5) (range 5))
;; ((0 0) (1 1) (2 2) (3 3) (4 4))
```

`xf/identity` is a simple transducer that takes care of of this case, assuming "identity" means "wrap around" in case of multiple inputs:

```clojure
(sequence (or (build-massive-xform) xf/identity) (range 5))
;; (0 1 2 3 4)

(sequence (or (build-massive-xform) xf/identity) (range 5) (range 5))
;; ((0 0) (1 1) (2 2) (3 3) (4 4))

(sequence (or (build-massive-xform) xf/identity) (range 5) (range 5) (range 5))
;; ((0 0 0) (1 1 1) (2 2 2) (3 3 3) (4 4 4))
```

`xf/identity` custom transducer compared to `(comp (map list) identity)` has also positive effects on performances:

```clojure
(let [items (range 10000)
      xform (comp (map list) identity)]
  (quick-bench
    (dorun
      (sequence xform items items))))
;; 4.09ms

(let [items (range 10000)]
  (quick-bench
    (dorun
      (sequence xf/identity items items))))
;; 2.67ms
```

## Development

There are no dependencies other than Java and Clojure.

* `lein test` to run the test suite.

#### misc todo

* [ ] `p/fold` Enable extend to (thread-safe) Java collections
* [ ] `p/fold` Enable extend on Cat objects
* [ ] `p/fold` operates on a group of keys for hash-maps.
* [ ] A foldable reader of some sort for large files.

## License

Copyright © 2018 Renzo Borgatti @reborg http://reborg.net
Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
