(defproject bundes "0.3.0"
  :description "a simple init system for mesos"
  :url "https://github.com/pyr/bundes"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :main bundes.main
  :dependencies [[org.clojure/clojure       "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.cli     "0.3.1"]
                 [org.clojure/core.match    "0.3.0-alpha4"]
                 [org.apache.mesos/mesos    "0.21.1"]
                 [org.spootnik/logconfig    "0.7.3"]
                 [org.spootnik/watchman     "0.3.3"]
                 [cc.qbits/jet              "0.5.7"]
                 [im.chit/cronj             "1.4.3"]
                 [ring/ring-defaults        "0.1.4"]
                 [ring/ring-core            "1.3.2"]
                 [ring/ring-json            "0.3.1"]
                 [compojure                 "1.3.2"]
                 [clj-yaml                  "0.4.0"]])
