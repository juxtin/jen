# jen

[![Clojars Project](http://clojars.org/jen/latest-version.svg)](http://clojars.org/jen)

A Clojure library that provides a more friendly syntax for defining [test.check](https://github.com/clojure/test.check) generators based on Clojure data structures, inspired by [Prismatic/schema](https://github.com/Prismatic/schema).

## motivation

The combination of [Prismatic/schema](https://github.com/Prismatic/schema) and [test.check](https://github.com/clojure/test.check) is great for catching bugs, but schema is way ahead in terms of clear, maintainable descriptions of typical data models.
The goal of this project is to bridge the gap by bringing some of the conveniences of schema's approach to test.check.

## example usage

Note: you *must* add a dependency on [test.check](https://github.com/clojure/test.check) yourself.

The main feature in jen is `jen.core/->generator`, which will turn just about anything into an ordinary test.check generator.

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
## supported forms and helper functions

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
* `with-recursive`: a macro that writes a recursive generator.
Explained in detail in the next section.
* `optional-key`: wraps a **hash-map key** (only!) and alters the map's generator so that the wrapped key (and its accompanying value) may not appear.
Note that unlike the above helper functions, this one **must** be used within a `jen.core/->generator` form.
* `optional`: wraps any value in a vector, set, or list (not hash-maps!) so that it may not appear at all in the generated structure.
Like `optional-key`, this function involves some magic and it won't work without `jen.core/->generator`.

### recursive generators

The implementations of recursive generators and recursive schemas are quite different, so a straightforward port of `schema.core/recursive` didn't seem feasible.
Instead, we have the `with-recursive` macro, which takes a vector of the form `[recur-symbol base-case]` and a jen-style generator (no need to call `->generator` yourself).

```clojure
(ns recursive-example
  (:require [jen.core :as jen]
            [clojure.test.check.generators :as gen]
            [schema.core :as sc]))

;; Here's the schema we're interested in, borrowed from https://github.com/Prismatic/schema/wiki/Recursive-Schemas
(def BinaryTree
  (sc/maybe ;; note that nil is a valid tree, so that's the base case
   {:value long
    :left (sc/recursive #'BinaryTree)
    :right (sc/recursive #'BinaryTree)}))

(def gen-binary-tree
  "A generator for binary trees using jen's `with-recursive` macro"
  (with-recursive [btree ;; we'll use btree to refer to this generator recursively
                   nil]  ;; the second part of the vector is the base (non-recursive) case
    {:value gen/int
     :left btree
     :right btree}))
```

The `gen-binary-tree` generator above is worth unpacking a bit.
As we can see from the `BinaryTree` schema, the `:left` and `:right` map keys can each be either another `BinaryTree` or `nil` (since the whole schema is wrapped in `sc/maybe`).
The `with-recursive` macro puts these two possibilities right up front: `[btree nil]`, where `btree` is the name we chose to refer to the generator and `nil` is the "base case."
Having a base case is important because it's the only thing that prevents us from generating an infinitely nested tree in this example.
The base case can be any value or generator.

Just for fun, here's an equivalent generator written in the standard test.check style, without jen:

```clojure
(def classic-gen-btree
  (let [recursive (fn [recur]
                    (gen/hash-map
                     :value gen/int
                     :left recur
                     :right recur))
        base-gen (gen/return nil)]
    (gen/recursive-gen recursive base-gen)))
```

Incidentally, that's pretty close to what the `with-recursive` macro generates.
I happen to think that the jen version is much clearer, but it's not quite as snappy as the original schema.
Though it does have the benefit that it doesn't rely on var-quoting, so recursive generators can still be anonymous.

## future plans

* ClojureScript support

Not planned: support for automatic conversion of schemas to generators.
Check out [zeeshanlakhani/schema-gen](https://github.com/zeeshanlakhani/schema-gen) if you're interested in that idea, but I believe that good, comprehensive schemas don't make good generators and vice-versa.
In order to fully take advantage of both technologies, you have to embrace their differences.

## license

Copyright © 2015 Justin Holguín

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
