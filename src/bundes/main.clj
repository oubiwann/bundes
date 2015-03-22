(ns bundes.main
  (:gen-class)
  (:require [bundes.unit            :as unit]
            [bundes.api             :as api]
            [bundes.watch           :as watch]
            [bundes.tick            :as tick]
            [bundes.cluster         :as cluster]
            [bundes.decisions       :refer [decisions]]
            [bundes.effect          :refer [perform-effect]]
            [org.spootnik.logconfig :refer [start-logging!]]
            [clj-yaml.core          :refer [parse-string]]
            [clojure.tools.logging  :refer [info debug]]
            [clojure.tools.cli      :refer [cli]]))

(defn read-config
  [path]
  (try
    (-> (or path (System/getenv "BUNDES_CONFIGURATION") "/etc/bundes/main.yml")
        slurp
        parse-string)
    (catch Exception o_O
      (binding [*out* *err*]
        (println "Could not read configuration: " (.getMessage o_O))
        (System/exit 1)))))

(defn get-cli
  [args]
  (try
    (cli args
         ["-h" "--help"  "Show Help"           :default false :flag true]
         ["-f" "--path"  "Configuration file"  :default nil])
    (catch Exception o_O
      (binding [*out* *err*]
        (println "Could not parse arguments: " (.getMessage o_O))
        (System/exit 1)))))

(defn converge-topology
  [_ _ old new]
  (let [side-effects (decisions old new)]
    (info "the world has changed!")
    (doseq [effect side-effects]
      (perform-effect effect))))

(defn start!
  [config]
  (cluster/cluster)
  (tick/start-ticker!)
  (let [db      (atom {})
        reg     (unit/atom-registry db)]
    (watch/watch-units reg (:unit-dir config))
    (converge-topology nil nil {} @db)
    (add-watch db :synchronizer converge-topology)
    (api/start! (:service config) reg)))

(defn -main
  [& args]
  (let [[opts args banner] (get-cli args)
        config             (read-config (:path opts))
        help?              (:help opts)]

    (start-logging! (:logging config))

    (when help?
      (println banner)
      (System/exit 0))

    (start! config)))
