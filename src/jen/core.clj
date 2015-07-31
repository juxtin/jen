(ns jen.core
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [clojure.test.check.generators :as gen]))

(declare map->generator)

(declare optional-key?)

(declare optional)

(declare remove-absent)

(declare bind-if)

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
    (map->generator x)

    (instance? clojure.lang.MapEntry x)
    (->generator (vec x))

    (vector? x)
    (->> x
         (map ->generator)
         (apply gen/tuple)
         (gen/fmap (comp vec remove-absent)))

    (set? x)
    (->> x
         (map ->generator)
         (apply gen/tuple)
         (gen/fmap (comp set remove-absent)))

    (list? x)
    (->> x
         (map ->generator)
         (apply gen/tuple)
         (gen/fmap (comp (partial apply list)
                         remove-absent)))

    (optional-key? x)
    (throw (IllegalArgumentException.
            (str "Error on " (.key x)
                 ". Only map keys may be wrapped in optional-key.")))

    :else
    (gen/return x)))

(deftype ^:private OptionalKey [key])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public helpers

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

(defn optional-key
  "Wraps the given key (in a map) so that it might not appear in the generated
  output. Analogous to schema.core/optional-key."
  [key]
  (OptionalKey. key))

(defn optional
  "Wraps the given value (in a list, set, or vector) so that it may not occur in
  the generated structure."
  [val]
  (gen/one-of [(gen/return ::remove) (->generator val)]))

(def optional-key? (partial instance? OptionalKey))

(defmacro with-recursive
  [[recur base] generator]
  `(let [gen-fn# (fn [g#]
                   (let [~recur g#]
                     (->generator ~generator)))]
     (gen/recursive-gen gen-fn# (->generator ~base))))

(deftype ^:private If [pred then else])

(defn if?
  [pred then else]
  {:pre (ifn? pred)}
  (If. pred (->generator then) (->generator else)))

(def if-type? (partial instance? jen.core.If))

(defn- if-replacer
  [new-gen if-type]
  (fn [x]
    (if (identical? x if-type)
      new-gen
      x)))

(defn- collect-if-types
  "Returns nil instead of empty"
  [coll]
  (let [candidates (if (map? coll)
                     (vals coll)
                     coll)]
    (not-empty (filter if-type? candidates))))

(defn- update-with-if-type
  [coll if-type]
  (let [pred (.pred if-type)
        new-gen (if (pred coll)
                  (.then if-type)
                  (.else if-type))
        replace (if-replacer new-gen if-type)]
    (fmap replace coll)))

(defn- contains-if?
  [coll]
  (-> coll
      collect-if-types
      not-empty
      boolean))

(defn bind-if
  ;; TODO: this inefficiently adds an extra step to all generators, just to
  ;; check if they have any conditionals. Since conditionals are intended to be
  ;; rare, that's a bad idea.

  ;; Better to check whether this is needed on a per-collection-type basis,
  ;; which would mean finally refactoring each of the ->generator cond bodies
  ;; into their own functions.
  [gen]
  (gen/bind gen
            (fn [val]
              (if-let [if-types (collect-if-types val)]
                (loop [acc     val
                       rem-ifs if-types]
                  (if-let [if-type (first rem-ifs)]
                    (recur (update-with-if-type acc if-type)
                           (next rem-ifs))
                    (->generator acc)))
                ;; else, no if-types present
                (->generator val)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation

(def ^:private map->flatseq
  "Returns a sequence of every key and value in the given map.

  Ex: {:foo 1, :bar 2} -> '(:foo 1 :bar 2)"
  (comp (partial apply concat) seq))

(defn- remove-absent
  "Given a collection that may contain ::remove'd optionals, actually remove them."
  [coll]
  (remove (partial = ::remove) coll))

(defn- unwrap-optional-key
  [key]
  (if (optional-key? key)
    (.key key)
    key))

(defn- optional-map
  "Returns the subset of the given map with optional keys."
  [m]
  (->> m
       (filter (comp optional-key? key))
       (map (fn [[k v]] [(unwrap-optional-key k) v]))
       (into {})))

(defn- required-map
  "Returns the subset of the given map with required keys. Keys are assumed to
  be required if they are not wrapped in optional-key."
  [m]
  (->> m
       (filter (comp not optional-key? key))
       (into {})))

(def ^:private split-map-by-required
  "Returns a pair of maps, the first of which contains only required keys and
  their values, while the second contains the optional kv pairs."
  (juxt required-map optional-map))

(defn- map->maybe-kvs
  "Given a map of optional key-value pairs, return a sequence of generators for [k v]
  pairs and/or nils. Map -> Gen Seq Maybe Pair"
  [m]
  (->> m
       (mapv maybe)
       ->generator))

(defn- maybe-kvs->map
  "Given a sequence of maybe kv-pairs, discard the nils and create a map
  out of the rest. Operates on concrete values, not generators."
  [kvs]
  (->> kvs
       (remove nil?)
       (into {})))

(defn- opt-map->map-gen
  "Given a map of (assumed) optional key-value pairs, return a generator for a map of some
  subset of them. Optional keys should be unwrapped *before* calling this function."
  [m]
  (->> (map->maybe-kvs m)
       (gen/fmap maybe-kvs->map)))

(defn- map-with-optional-keys
  "Returns a generator for the given map schema, which is assumed to contain one
  or more optional keys."
  [m]
  (let [[required optional] (split-map-by-required m)
        gen-opt-map (opt-map->map-gen optional)]
    (-> gen-opt-map
        (gen/bind (fn [opt-map]
                    (->generator (merge opt-map required)))))))

(defn- map->generator
  "Returns a generator for the given map schema, which may or may not include any optional keys."
  [m]
  (let [gen (if (some (comp optional-key? key) m)
              (map-with-optional-keys m)
              (->> m
                   (fmap ->generator)
                   map->flatseq
                   (apply gen/hash-map)))]
    (if (contains-if? m)
      (bind-if gen)
      gen)))
