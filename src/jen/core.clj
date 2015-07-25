(ns jen.core
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [clojure.set :as set]
            [clojure.test.check.generators :as gen]
            [clojure.walk :as walk]))

(def ^:private map->flatseq (comp (partial apply concat) seq))

(defn ->generator
  "Create a generator out of any value. Recursively walks maps, vectors, lists,
  and sets to build a generator for an equivalent data structure. Generators
  found within data structures are treated as positional, i.e., `[gen/int]` will
  generate a vector with one integer rather than a vector of n integers.

  Examples with (require '[clojure.test.check.generators :as gen]):
  (gen/sample (->generator
               {:int gen/int
                :char-vec [gen/char gen/char]}))
  #_=> '({:int 0, :char-vec [\t, \6]} ...)

  (gen/sample (->generator #{gen/keyword}))
  #_=> '(#{:b} ...)

  (gen/sample (->generator
               {:kw-int-map (gen/map gen/keyword gen/int)}))
  #_=> '({:kw-int-map {:nV:21+:* -2}} ...)"
  [x]
  (cond
    (gen/generator? x)
    x

    (map? x)
    (->> x
         (fmap ->generator)
         map->flatseq
         (apply gen/hash-map))

    (vector? x)
    (->> x
         (fmap ->generator)
         (apply gen/tuple)
         (gen/fmap vec))

    (set? x)
    (->> x
         (fmap ->generator)
         (apply gen/tuple)
         (gen/fmap set))

    (list? x)
    (->> x
         (map ->generator)
         (apply gen/tuple)
         (gen/fmap (partial apply list)))

    :else
    (gen/return x)))

(defn maybe
  "Returns a generator that generates either nil or a value from the supplied
  generator. Converts its argument to a generator if necessary."
  [x]
  (gen/one-of [(gen/return nil) (->generator x)]))

(defn enum
  "Returns a generator that generates one of the exact values in the given
  collection. Does not convert elements into generators: see `either` if you need
  more flexibility."
  [& vals]
  (gen/one-of (map gen/return vals)))

(defn either
  "Returns a generator that generates from one of the generators in the
  given collection. Will convert elements into generators if necessary."
  [& xs]
  (gen/one-of (mapv ->generator xs)))
