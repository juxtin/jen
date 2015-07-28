(ns jen.test-util
  (:require  [jen.core :as jen]
             [clojure.test.check.generators :as gen]
             [clojure.test.check.properties :as prop]
             [schema.core :as sc]))

(defn generator-fits-schema-prop
  "The property that the generator always fits the given schema."
  [generator schema]
  (let [fits-schema? (comp nil? (sc/checker schema))]
    (prop/for-all [x generator]
      (fits-schema? x))))

(defn generator-sometimes-fits-schema?
  "Returns true if the generator generates at least one example that fits the
  schema within n tries. Defaults to 100 attempts. Not a property!"
  ([generator schema] (generator-sometimes-fits-schema? generator schema 100))
  ([generator schema n]
   (let [fits-schema? (comp nil? (sc/checker schema))
         ;; for some reason, take n works better than the optional n argument to
         ;; sample-seq
         examples (take n (gen/sample-seq generator))]
     (boolean (some fits-schema? examples)))))
