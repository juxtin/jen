(ns jen.core-test
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [jen.core :refer :all]))

(deftest map->flatseq-test
  (testing "doesn't flatten keys or vals"
    (is (= '(:vec [1 2 3])
           (map->flatseq {:vec [1 2 3]})))
    (is (= '([:key :vec] :val)
           (map->flatseq {[:key :vec] :val})))))

(defspec map->flatseq-round-trip
  100
  (prop/for-all [m (gen/hash-map :val1 gen/any
                                 :val2 gen/any
                                 :val3 gen/any)]
    (= m (apply hash-map (map->flatseq m)))))

(def example
  {:int gen/int
   :char gen/char
   :generator (gen/map gen/keyword gen/symbol)
   :map {:list (list gen/char gen/char)
         :vector [1 2 3]
         :number 666}
   :set #{gen/int gen/char}
   :vec [gen/int gen/char]
   :values {:string "hebbo"
            :char \c
            :fn identity
            :lazy (range 1 4)
            :map {:key1 :val1, :key2 :val2}
            :set #{'one 2 :three}
            :empty-map {}}
   :mixed {:vec [gen/int \t gen/symbol 4]
           :map {:three 3
                 :int gen/int}
           :set #{'one gen/int :three}
           :list (list gen/int 2 gen/char)}})

(def example-schema
  {:int sc/Int
   :char (sc/pred char?)
   :generator {sc/Keyword sc/Symbol}
   :map {:list (sc/pred #(and (list? %)
                              (every? char? %)
                              (= 2 (count %))))
         :vector (sc/eq [1 2 3])
         :number (sc/eq 666)}
   :set (sc/pred #(and (set? %)
                       (some integer? %)
                       (some char? %)
                       (= 2 (count %))))
   :vec (sc/pred #(and (vector? %)
                       (integer? (first %))
                       (char? (second %))
                       (= 2 (count %))))
   :values {:string (sc/eq "hebbo")
            :char (sc/eq \c)
            :fn (sc/eq identity)
            :lazy (sc/eq (range 1 4))
            :map (sc/eq {:key1 :val1, :key2 :val2})
            :set (sc/eq #{'one 2 :three})
            :empty-map (sc/eq {})}
   :mixed {:vec (sc/pred #(and (vector? %)
                               (= 4 (count %))
                               (let [[a b c d] %]
                                 (and (integer? a)
                                      (= \t b)
                                      (symbol? c)
                                      (= 4 d)))))
           :map {:three (sc/eq 3)
                 :int sc/Int}
           :set (sc/pred #(and (set? %)
                               (contains? % 'one)
                               (contains? % :three)
                               (some integer? %)
                               (= 3 (count %))))
           :list (sc/pred #(and (list? %)
                                (= 3 (count %))
                                (integer? (first %))
                                (= 2 (second %))
                                (char? (last %))))}})

(defspec validate-complex-generator
  100
  (prop/for-all [m (->generator example)]
    (nil? (sc/check example-schema m))))

