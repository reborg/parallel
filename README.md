## parallel

`parallel` is a library of parallel-enabled Clojure functions. They are designed to emulate similar functions already
present in the standard library (sometimes as a drop-in replacement, sometimes with a radically different semantic) to run
in parallel on multiple cores on a single machine. It also provides new transducers (and reducers).

It currently contains the following:

Name                  | Type         | Description
-------------------   | ------------ | ---------------------------------------------------
* [p] `p/interleave`  | Transducer   | Like `core/interleave`
* [ ] `p/drop`        | Transducer   | Parallel-enabled `core/drop`
* [ ] `p/jfold`       | Function     | Enables `reducers/fold` on mutable Java collections
* [ ] `p/merge-sort`  | Function     | Memory efficient parallel merge-sort
* [p] `p/update-vals` | Function     | Updates all values in a map in parallel.
* [ ] `p/mapv`        | Function     | Transform a vector in parallel and returns a vector.
* [ ] `p/filterv`     | Function     | Filter a vector in parallel and returns a vector.
* [p] `p/frequencies` | Function     | Like `core/frequencies` but in parallel.

Please see below for detailed information on how to use them.

## How to use the library

Transducers, reducers and related utilities are available through the `parallel` namespace.
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

### `p/interleave`

### `p/frequencies`

### `p/update-vals`

## License

Copyright © 2017 Renzo Borgatti @reborg http://reborg.net

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
