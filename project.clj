(defproject jen "0.1.3-SNAPSHOT"
  :description "Schema-style generators for test.check"
  :url "https://github.com/holguinj/jen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/algo.generic "0.1.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [prismatic/schema "0.4.3"]
                                  [org.clojure/test.check "0.7.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :codox {:output-dir "doc"})
