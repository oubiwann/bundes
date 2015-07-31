(defproject bundes "0.3.0"
  :description "a simple init system for mesos"
  :url "https://github.com/pyr/bundes"
  :license {:name "MIT License"}
  :aot :all
  :main bundes.main
  :plugins [[lein-cljsbuild "1.0.5"
             :exclusions [org.clojure/clojure]]
            [lein-figwheel  "0.3.1"
             :exclusions [org.clojure/clojure
                          org.codehaus.plexus/plexus-utils]]]
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure                  "1.7.0"]
                 [org.clojure/tools.logging            "0.3.1"]
                 [org.clojure/tools.cli                "0.3.2"]
                 [org.clojure/core.match               "0.3.0-alpha4"]
                 [spootnik/mesomatic                   "0.23.0-r0"]
                 [spootnik/mesomatic-async             "0.23.0-r0"]
                 [spootnik/unilog                      "0.7.8"]
                 [spootnik/watchman                    "0.3.5"]
                 [cc.qbits/jet                         "0.6.6"]
                 [im.chit/cronj                        "1.4.3"]
                 [ring/ring-defaults                   "0.1.5"]
                 [ring/ring-core                       "1.4.0"
                  :exclusions [org.clojure/tools.reader]]
                 [ring/ring-json                       "0.4.0"]
                 [compojure                            "1.4.0"]
                 [clj-yaml                             "0.4.0"]
                 [org.apache.curator/curator-recipes   "2.8.0"]
                 [org.apache.curator/curator-framework "2.8.0"]
                 [riemann-clojure-client               "0.4.1"]

                 ;; clojurescript deps
                 [org.clojure/clojurescript            "1.7.10"]
                 [org.clojure/core.async               "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om                        "0.9.0"]
                 [figwheel                             "0.3.7"]
                 [sablono                              "0.3.4"]
                 [secretary                            "1.2.3"]
                 [bidi                                 "1.20.2"]
                 [prismatic/schema                     "0.4.3"]
                 [prismatic/plumbing                   "0.4.4"]
                 [racehub/om-bootstrap                 "0.5.3"]]
  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"
                                    "figwheel_server.log"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :figwheel true
                        :compiler {:main       bundes.client
                                   :asset-path "js/compiled/app"
                                   :output-to  "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/app"
                                   :source-map-timestamp true}}
                       {:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:main bundes.client
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :pretty-print false}}]}
  :figwheel {:css-dirs ["resources/public/css"]})
