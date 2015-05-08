(defproject bundes "0.3.0"
  :description "a simple init system for mesos"
  :url "https://github.com/pyr/bundes"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :main bundes.main
  :plugins [[lein-cljsbuild "1.0.5"
             :exclusions [org.clojure/clojure]]
            [lein-figwheel  "0.3.1"
             :exclusions [org.clojure/clojure
                          org.codehaus.plexus/plexus-utils]]]
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure          "1.7.0-beta2"]
                 [org.clojure/tools.logging    "0.3.1"]
                 [org.clojure/tools.cli        "0.3.1"]
                 [org.clojure/core.match       "0.3.0-alpha4"]
                 [org.spootnik/mesomatic       "0.22.0-r2"]
                 [org.spootnik/mesomatic-async "0.22.0-r2"]
                 [org.spootnik/logconfig       "0.7.3"]
                 [org.spootnik/watchman        "0.3.3"]
                 [cc.qbits/jet                 "0.5.7"]
                 [im.chit/cronj                "1.4.3"]
                 [ring/ring-defaults           "0.1.4"]
                 [ring/ring-core               "1.3.2"
                  :exclusions [org.clojure/tools.reader]]
                 [ring/ring-json               "0.3.1"]
                 [compojure                    "1.3.3"]
                 [clj-yaml                     "0.4.0"]

                 ;; clojurescript deps
                 [org.clojure/clojurescript    "0.0-3211"]
                 [org.omcljs/om                "0.8.8"]
                 [figwheel                     "0.3.1"]
                 [sablono                      "0.3.4"]
                 [secretary                    "1.2.3"]
                 [racehub/om-bootstrap         "0.5.0"]]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :figwheel true
                        :compiler {:main       bundes.client
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true}}]}
  :figwheel {:css-dirs ["resources/public/css"]})
