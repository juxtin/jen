# jen

[![Clojars Project](http://clojars.org/jen/latest-version.svg)](http://clojars.org/jen)

A Clojure library that provides a more friendly syntax for defining [test.check](https://github.com/clojure/test.check) generators based on Clojure data structures, inspired by [Prismatic/schema](https://github.com/Prismatic/schema).

## Motivation

The combination of [Prismatic/schema](https://github.com/Prismatic/schema) and [test.check](https://github.com/clojure/test.check) is great for catching bugs, but schema is way ahead in terms of clear, maintainable descriptions of typical data models.
The goal of this project is to bridge the gap by bringing some of the conveniences of schema's approach to test.check.

## Example Usage

Note: you *must* add a dependency on [test.check](https://github.com/clojure/test.check) yourself.

```clojure
(ns jen-examples
  (:require [jen.core :as jen]
            [clojure.test.check.generators :as gen]))

;; a simple, two-element vector
(gen/sample (jen/->generator [gen/int gen/char]))
;; ([0 \°] [1 \] [-2 \7] [1 \¼] [-3 \] [3 \È] [4 \Ë] [-2 \¡] [2 \y] [9 \])

;; a map!
(def gen-user
  (jen/->generator {:name (gen/not-empty gen/string-alphanumeric)
                    :password (gen/not-empty gen/string-alphanumeric)
                    :logins gen/pos-int}))
(gen/sample gen-user)
;; ({:name "A2FH", :password "Plj", :logins 0} ...)

;; jen's generators are just test.check generators
(gen/sample (gen/vector gen-user 3))
;; ([{:name "z", :password "tIk", :logins 3}
;;   {:name "8d2m", :password "kfPt", :logins 5}
;;   {:name "LQ5f", :password "l", :logins 6}], ...)

;; and can be easily combined
(def gen-page
  (jen/->generator {:author gen-user
                    :title gen/string-alphanumeric
                    :views gen/pos-int
                    (jen/optional-key :tags) (gen/vector (jen/enum "#yolo" "#robots" "#cyberpunk"))}))

(gen/sample gen-page)
;; ({:author {:name "M", :password "wo6ubp", :logins 3}
;;   :title "9h"
;;   :views 1
;;   :tags ["#yolo"]} ...)
```
## Supported forms and helper functions

The following data types are supported by `jen.core/->generator`:

* hash-maps
* sets
* vectors
* lists

Any generators found in a supported data structure will remain in the same position in the composed generator.
Bare values like strings, keywords, and numerics will remain static in the composed generator.

These helper functions are provided to provide some familiar conveniences for schema users:

* `maybe`: wraps any form and results in a generator that may select `nil`.
* `enum`: wraps any number of bare values (not generators) and returns a generator that selects one of those values.
* `either`: wraps any number of forms (including generators) and returns a generator that selects from the provided values/generators.
* `optional-key`: wraps a **hash-map key** (only!) and alters the map's generator so that the wrapped key (and its accompanying value) may not appear.
Note that unlike the other helper functions, this one **must** be used within a `jen.core/->generator` form.

## Future plans

* ClojureScript support
* an `optional` generator for vector/list/set types (`optional-key` is already implemented)
* recursive generators

Not planned: support for automatic conversion of schemas to generators.
Check out [zeeshanlakhani/schema-gen](https://github.com/zeeshanlakhani/schema-gen) if you're interested in that idea, but I believe that good, comprehensive schemas don't make good generators and vice-versa.
In order to fully take advantage of both technologies, you have to embrace their differences.

## License

Copyright © 2015 Justin Holguín

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
