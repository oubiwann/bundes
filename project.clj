(defproject bundes "0.3.0"
  :description "a simple init system for mesos"
  :url "https://github.com/pyr/bundes"
  :license {:name "MIT License"}
  :aot :all
  :main bundes.main
  :dependencies [[org.clojure/clojure                  "1.7.0"]
                 [org.clojure/tools.logging            "0.3.1"]
                 [org.clojure/tools.cli                "0.3.2"]
                 [org.clojure/core.match               "0.3.0-alpha4"]
                 [spootnik/mesomatic                   "0.23.0-r0"]
                 [spootnik/mesomatic-async             "0.23.0-r0"]
                 [spootnik/unilog                      "0.7.8"]
                 [spootnik/watchman                    "0.3.5"]
                 [spootnik/net                         "0.2.7"]
                 [im.chit/cronj                        "1.4.4"]
                 [ring/ring-defaults                   "0.1.5"]
                 [ring/ring-core                       "1.4.0"
                  :exclusions [org.clojure/tools.reader]]
                 [ring/ring-json                       "0.4.0"]
                 [compojure                            "1.4.0"]
                 [clj-yaml                             "0.4.0"]
                 [org.apache.curator/curator-recipes   "2.8.0"]
                 [org.apache.curator/curator-framework "2.8.0"]
                 [riemann-clojure-client               "0.4.1"]])
