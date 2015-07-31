(ns jen.core-test
  (:require [clojure.test :refer :all]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [jen.test-util :as test]
            [clojure.test.check.properties :as prop]
            [jen.core :refer :all :as jen]))

(def map->flatseq
  "Hacky import of private function"
  @#'jen.core/map->flatseq)

(deftest generator-sometimes-fits-schema-test
  (let [gen gen/int]
    (is (false? (test/generator-sometimes-fits-schema? gen sc/Str 10)))
    (is (test/generator-sometimes-fits-schema? gen sc/Int 1))))

(defspec map->flatseq-round-trip-spec
  25
  (prop/for-all [m (gen/map gen/any gen/any)]
    (= m (apply hash-map (map->flatseq m)))))

(deftest map->flatseq-test
  (testing "doesn't flatten keys or vals"
    (is (= '(:vec [1 2 3])
           (map->flatseq {:vec [1 2 3]})))
    (is (= '([:key :vec] :val)
           (map->flatseq {[:key :vec] :val})))))

(defspec ->generator-does-not-change-maps-spec
  50
  ;; as long as a map does not contain a generator, ->generator should turn
  ;; it into a generator that only generates the original map
  (prop/for-all [original (gen/map gen/any gen/any)]
    (let [gen (->generator original)
          generated-example (first (gen/sample gen 1))]
      (= original generated-example))))

(defspec map-with-optional-keys-spec
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
    (test/generator-fits-schema-prop gen schema)))

(deftest map-with-optional-keys-test
  (let [gen (->generator {:req gen/int
                          (optional-key :opt-1) gen/char-alpha
                          (optional-key :opt-2) gen/char-alpha})
        schema-no-optionals {:req sc/Int}
        schema-opt-1 (assoc schema-no-optionals :opt-1 (sc/pred char?))
        schema-opt-2 (assoc schema-no-optionals :opt-2 (sc/pred char?))
        schema-with-both (merge schema-opt-1 schema-opt-2)]
    (is (test/generator-sometimes-fits-schema? gen schema-no-optionals))
    (is (test/generator-sometimes-fits-schema? gen schema-opt-1))
    (is (test/generator-sometimes-fits-schema? gen schema-opt-2))
    (is (test/generator-sometimes-fits-schema? gen schema-with-both))))

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

(defspec validate-complex-generator-spec
  100
  (let [gen (->generator example)]
    (test/generator-fits-schema-prop gen example-schema)))

(defspec maybe-spec
  5
  (let [maybe-one-int-vec (sc/maybe (sc/pred #(and (vector %)
                                                   (= 1 (count %))
                                                   (integer? (first %)))))]
    (test/generator-fits-schema-prop (maybe [gen/int]) maybe-one-int-vec)))

(deftest maybe-test
  (let [gen (maybe [gen/int])]
    (is (test/generator-sometimes-fits-schema? gen (sc/eq nil)))
    (is (test/generator-sometimes-fits-schema? gen [(sc/one sc/Int "int")]))))

(defspec enum-spec
  25
  (let [gen (enum false 2 3 5 7 11)
        false-or-prime (sc/enum false 2 3 5 7 11)]
    (test/generator-fits-schema-prop gen false-or-prime)))

(deftest enum-test
  (let [gen (enum :night :day)]
    (is (test/generator-sometimes-fits-schema? gen (sc/eq :night)))
    (is (test/generator-sometimes-fits-schema? gen (sc/eq :day)))))

(defspec either-spec
  10
  (let [gen (either gen/int :cool (gen/vector gen/char))
        int-cool-or-charvec (sc/either sc/Int
                                       (sc/eq :cool)
                                       [(sc/pred char?)])]
    (test/generator-fits-schema-prop gen int-cool-or-charvec)))

(deftest either-test
  (let [gen (either gen/int :radical (gen/vector gen/boolean))]
    (is (test/generator-sometimes-fits-schema? gen sc/Int))
    (is (test/generator-sometimes-fits-schema? gen (sc/eq :radical)))
    (is (test/generator-sometimes-fits-schema? gen [(sc/enum true false)]))))

(defspec optional-vec-spec
  15
  (let [vec-with-optional (->generator [(optional "Optional") gen/int])
        test-schema (sc/pred #(and (vector? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (= "Optional" (first %))
                                     true)))]
    (test/generator-fits-schema-prop vec-with-optional test-schema)))

(deftest optional-vec-test
  (let [gen (->generator [(optional :opty) gen/int])]
    (is (test/generator-sometimes-fits-schema? gen [(sc/one (sc/eq :opty) "Opty") (sc/one sc/Int "int")]))
    (is (test/generator-sometimes-fits-schema? gen [(sc/one sc/Int "int")]))))

(defspec optional-list-spec
  15
  (let [list-with-optional (->generator (list (optional "Optional") gen/int))
        test-schema (sc/pred #(and (list? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (= "Optional" (first %))
                                     true)))]
    (test/generator-fits-schema-prop list-with-optional test-schema)))

(deftest optional-list-test
  (let [gen (->generator (list (optional :opty) gen/int))]
    (is (test/generator-sometimes-fits-schema? gen [(sc/one (sc/eq :opty) "opty") (sc/one sc/Int "int")]))
    (is (test/generator-sometimes-fits-schema? gen [(sc/one sc/Int "int")]))))

(defspec optional-set-spec
  15
  (let [set-with-optional (->generator #{(optional "Optional") gen/int})
        test-schema (sc/pred #(and (set? %)
                                   (<= 1 (count %) 2)
                                   (some integer? %)
                                   (if (= 2 (count %))
                                     (contains? % "Optional")
                                     true)))]
    (test/generator-fits-schema-prop set-with-optional test-schema)))

(deftest optional-set-test
  (let [gen (->generator #{(optional :opty) gen/int})]
    (is (test/generator-sometimes-fits-schema? gen (sc/pred #(and (set? %)
                                                                  (= 1 (count %))
                                                                  (every? integer? %)))))
    (is (test/generator-sometimes-fits-schema? gen (sc/pred #(and (set? %)
                                                                  (= 2 (count %))
                                                                  (contains? % :opty)
                                                                  (some integer? %)))))))

(def BinaryTree
  "A recursive schema for testing recursive generators. Borrowed from the
  Prismatic/schema docs."
  (sc/maybe
   {:value long
    :left (sc/recursive #'BinaryTree)
    :right (sc/recursive #'BinaryTree)}))

(def gen-binary-tree
  (with-recursive [btree nil]
    {:value gen/int
     :left btree
     :right btree}))

(defspec recursive-map-spec
  25
  (test/generator-fits-schema-prop gen-binary-tree BinaryTree))

(deftest recursive-map-test
  (testing "when generating a binary tree"
    (let [base {:value sc/Int
                :left (sc/eq nil)
                :right (sc/eq nil)}
          left-child (assoc base :left BinaryTree)
          right-child (assoc base :right BinaryTree)
          both-children (assoc left-child :right BinaryTree)]
      ;; Transient failures here are possible (and permissible), but they should
      ;; be very rare.
      (testing "the tree"
        (testing "sometimes has no children"
          (is (test/generator-sometimes-fits-schema? gen-binary-tree base 50)))
        (testing "sometimes has only a left child"
          (is (test/generator-sometimes-fits-schema? gen-binary-tree left-child 50)))
        (testing "sometimes has only a right child"
          (is (test/generator-sometimes-fits-schema? gen-binary-tree right-child 50)))
        (testing "sometimes has two children"
          (is (test/generator-sometimes-fits-schema? gen-binary-tree both-children 50)))))))

(def NestedBoolVec
  (sc/either (sc/enum true false)
             [(sc/recursive #'NestedBoolVec)]))

(defspec recursive-vec-spec
  25
  (let [gen-nested-bool-vec (with-recursive [nested-vec gen/boolean]
                              (gen/vector nested-vec))]
    (test/generator-fits-schema-prop gen-nested-bool-vec NestedBoolVec)))

(defspec map-single-if?-gen-spec
  25
  (let [if-gen (->generator
                {:num gen/int
                 :even? (if? (comp even? :num)
                             true
                             false)})
        base {:num sc/Int}
        if-schema (sc/if (comp even? :num)
                    (assoc base :even? (sc/eq true))
                    (assoc base :even? (sc/eq false)))]
    (test/generator-fits-schema-prop if-gen if-schema)))

(defspec map-double-if?-gen-spec
  25
  (let [if-gen (->generator
                {:num gen/int
                 :type (enum :int :char)
                 :even? (if? (comp even? :num)
                               true
                               false)
                 :example (if? #(= :int (:type %))
                               gen/int
                               gen/char-alpha)})
        basic-schema {:num sc/Int
                      :type (sc/enum :int :char)
                      :even? (sc/enum true false)
                      :example (sc/either sc/Int (sc/pred char?))}
        if-schema (sc/pred (fn [{:keys [num type even? example]}]
                             (and (= even? (clojure.core/even? num))
                                  (if (= :int type)
                                    (integer? example)
                                    (char? example)))))
        schema (sc/both basic-schema if-schema)]
    (test/generator-fits-schema-prop if-gen schema)))

(defspec vec-single-if?-gen-spec
  25
  (let [if-gen (->generator
                [(if? (comp integer? second)
                      :int
                      :char)
                 (either gen/int gen/char)])
        schema (sc/pred (fn [v]
                          (and (vector? v)
                               (= 2 (count v))
                               (if (= :int (first v))
                                 (integer? (second v))
                                 (char? (second v))))))]
    (test/generator-fits-schema-prop if-gen schema)))
