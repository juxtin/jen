(ns jen.core-test
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [jen.core :refer :all]))

(def map->flatseq
  "Hacky import of private function"
  @#'jen.core/map->flatseq)

(deftest map->flatseq-test
  (testing "doesn't flatten keys or vals"
    (is (= '(:vec [1 2 3])
           (map->flatseq {:vec [1 2 3]})))
    (is (= '([:key :vec] :val)
           (map->flatseq {[:key :vec] :val})))))

(defspec map->flatseq-round-trip
  25
  (prop/for-all [m (gen/hash-map :val1 gen/any
                                 :val2 gen/any
                                 :val3 gen/any)]
    (= m (apply hash-map (map->flatseq m)))))

(defspec map-with-optional-keys-test
  25
  (let [gen (->generator
             {:req-int gen/int
              :req-char gen/char
              :req-vec [gen/string-alphanumeric gen/boolean]
              :nested {(optional-key :keyword) gen/keyword}
              (optional-key :opt-int) gen/int
              (optional-key :opt-char) gen/char
              (optional-key :opt-vec) [gen/int gen/int gen/int]})
        schema {:req-int sc/Int
                :req-char (sc/pred char?)
                :req-vec (sc/pred #(and (vector? %)
                                        (= 2 (count %))
                                        (string? (first %))
                                        (contains? #{true false} (second %))))
                :nested {(sc/optional-key :keyword) sc/Keyword}
                (sc/optional-key :opt-int) sc/Int
                (sc/optional-key :opt-char) (sc/pred char?)
                (sc/optional-key :opt-vec) (sc/pred #(and (vector? %)
                                                          (= 3 (count %))
                                                          (every? integer? %)))}]
    (prop/for-all [x gen]
      (nil? (sc/check schema x)))))

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
                               (= 3 (count %))
                               (contains? % 'one)
                               (some integer? %)
                               (contains? % :three)))
           :list (sc/pred #(and (list? %)
                                (= 3 (count %))
                                (integer? (first %))
                                (= 2 (second %))
                                (char? (last %))))}})

(defspec validate-complex-generator
  100
  (prop/for-all [m (->generator example)]
    (nil? (sc/check example-schema m))))

(defspec maybe-test
  5
  (let [maybe-one-int-vec (sc/maybe (sc/pred #(and (vector %)
                                                   (= 1 (count %))
                                                   (integer? (first %)))))]
    (prop/for-all [v (maybe [gen/int])]
      (nil? (sc/check maybe-one-int-vec v)))))

(defspec enum-test
  25
  (let [false-or-prime (sc/enum [false 2 3 5 7 11])]
    (prop/for-all [x (enum [false 2 3 5 7 11])]
      (nil? (sc/check false-or-prime x)))))

(defspec either-test
  10
  (let [int-cool-or-charvec (sc/either sc/Int
                                       (sc/eq :cool)
                                       [(sc/pred char?)])]
    (prop/for-all [x (either gen/int :cool (gen/vector gen/char))]
      (nil? (sc/check int-cool-or-charvec x)))))

(defspec optional-vec-test
  15
  (let [vec-with-optional (->generator [(optional "Optional") gen/int])
        test-schema (sc/pred #(and (vector? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (= "Optional" (first %))
                                     true)))]
    (prop/for-all [v vec-with-optional]
      (nil? (sc/check test-schema v)))))

(defspec optional-list-test
  15
  (let [list-with-optional (->generator (list (optional "Optional") gen/int))
        test-schema (sc/pred #(and (list? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (= "Optional" (first %))
                                     true)))]
    (prop/for-all [v list-with-optional]
      (nil? (sc/check test-schema v)))))

(defspec optional-set-test
  15
  (let [set-with-optional (->generator #{(optional "Optional") gen/int})
        test-schema (sc/pred #(and (set? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (contains? % "Optional")
                                     true)))]
    (prop/for-all [v set-with-optional]
      (nil? (sc/check test-schema v)))))
