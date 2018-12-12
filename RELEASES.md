## Release History

### 0.9 (WIP)

* `p/frequencies` does not need a special keyfn.
* Added `p/pmap`

### 0.8

* Using `p/transduce` to implement frequencies.
* Added ./examples

### 0.7

* Fix bug in `p/armap` for the sequential case.
* Fixed missing laziness in external-sort
* Added `p/do` and `p/doto`
* Added `p/transduce`
* Added `p/process-folder`

### 0.6

* Added `p/slurp`
* Consolidated and documented `p/min` and `p/max`

### 0.5

* Added `xf/identity` transducer.
* Added `p/let` parallel let bindings.

### 0.4

* Added `p/armap`, parallel array reverse.
* Added `xf/pmap`, transducer version of `core/pmap`.

### 0.3

* Added `p/distinct`

### 0.2

* Moved `parallel` namespace to `core` to avoid potential Java interop problems (see #3).

### 0.1

First batch of functions.
