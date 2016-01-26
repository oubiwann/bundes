(defproject bundes "0.3.0"
  :description "a simple init system for mesos"
  :url "https://github.com/pyr/bundes"
  :license {:name "MIT License"}
  :aot :all
  :main bundes.main
  :dependencies [[org.clojure/clojure                  "1.8.0"]
                 [org.clojure/tools.logging            "0.3.1"]
                 [org.clojure/tools.cli                "0.3.2"]
                 [org.clojure/core.match               "0.3.0-alpha4"]
                 [com.stuartsierra/component           "0.3.1"]
                 [prismatic/schema                     "1.0.4"]
                 [spootnik/mesomatic                   "0.27.0-r0"]
                 [spootnik/mesomatic-async             "0.27.0-r0"]
                 [spootnik/unilog                      "0.7.13"]
                 [spootnik/watchman                    "0.3.5"]
                 [spootnik/net                         "0.2.8"]
                 [spootnik/reporter                    "0.1.3"]
                 [spootnik/uncaught                    "0.5.3"]
                 [riemann-clojure-client               "0.4.1"
                  :exclusions [com.aphyr/riemann-java-client]]
                 [metrics-clojure                      "2.6.1"]
                 [metrics-clojure-riemann              "2.6.1"]
                 [metrics-clojure-jvm                  "2.6.1"]
                 [cheshire                             "5.5.0"]
                 [bidi                                 "1.25.0"]
                 [im.chit/cronj                        "1.4.4"]
                 [riemann-clojure-client               "0.4.1"]])
