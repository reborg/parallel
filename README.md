## xduce

`xduce` is a Clojure library of [Transducers](https://clojure.org/reference/transducers) and [Reducers](https://clojure.org/reference/reducers)
with specific focus on parallelism. It currently contains the following:

Name                  | Type         | Description
-------------------   | ------------ | ---------------------------------------------------
* [x] `x/interleave`  | Transducer   | Like `core/interleave`
* [ ] `x/drop`        | Transducer   | Parallel-enabled `core/drop`
* [ ] `x/jfold`       | Function     | Enables `reducers/fold` on mutable Java collections
* [ ] `x/merge-sort`  | Function     | Memory efficient parallel merge-sort
* [x] `x/update-vals` | Function     | Updates all values in a map in parallel.
* [ ] `x/mapv`        | Function     | Transform a vector in parallel and returns a vector.
* [ ] `x/filterv`     | Function     | Filter a vector in parallel and returns a vector.
* [x] `x/frequencies` | Function     | Like `core/frequencies` but in parallel.

Please see below for detailed information on how to use them.

## How to use the library

Transducers, reducers and related utilities are available through the `xduce` namespace.
Add the following to your project dependencies:

```clojure
[xduce "0.1"]
```

Then require at the REPL with:

```clojure
(require '[xduce :as x])
```

Or in your namespace as:

```clojure
(ns mynamespace
  (:require [xduce :as x]))
```

## API Docs

### `x/interleave`

### `x/frequencies`

### `x/update-vals`

## License

Copyright © 2017 Renzo Borgatti @reborg http://reborg.net

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
