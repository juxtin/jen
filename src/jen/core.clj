(ns jen.core
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [clojure.set :as set]
            [clojure.test.check.generators :as gen]
            [clojure.walk :as walk]))

(def map->flatseq (comp (partial apply concat) seq))

(defn ->generator
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
  [x]
  (gen/one-of [(gen/return nil) (->generator x)]))
