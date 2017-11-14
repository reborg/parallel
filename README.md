## xduce

`xduce` is a Clojure library of [Transducers](https://clojure.org/reference/transducers) and [Reducers](https://clojure.org/reference/reducers)
with specific focus on parallelism. It contains the following:

Name                | Type         | Description
------------------- | ------------ | -----------------------------------------------------------
* [ ] `x/interleave`| Transducer   | Like `core/interleave`
* [ ] `x/drop`      | Transducer   | Parallel-enabled `core/drop`
* [ ] `x/jfold`     | Reducer      | Enables `reducers/fold` on mutable Java collections
* [ ] `x/merge-sort`| Reducer      | Memory efficient parallel merge-sort
* [ ] `x/mapv`      | Reducer      | Transform a vector in parallel and returns a vector.
* [ ] `x/filterv`   | Reducer      | Filter a vector in parallel and returns a vector.

## How to use the library

Transducers, reducers and related utilities are available through the `xduce` namespace.
Adds the following to your project dependencies:

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

### `x/drop`

## License

Copyright © 2017 Renzo Borgatti @reborg http://reborg.net

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
