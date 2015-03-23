(ns bundes.main
  (:gen-class)
  (:require [bundes.unit            :as unit]
            [bundes.api             :as api]
            [bundes.watch           :as watch]
            [bundes.tick            :as tick]
            [bundes.mesos           :as mesos]
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
  [system _ _ old new]
  (let [side-effects (decisions old new)]
    (info "the world has changed, converging!")
    (doseq [effect side-effects]
      ;; We merge the system map onto the action description
      (perform-effect (merge system effect)))))

(defn start!
  [config]
  (let [db      (atom {})
        reg     (unit/atom-registry db)
        system  {:ticker  (tick/create!)
                 :cluster (mesos/framework! config)}]
    (watch/watch-units reg (:unit-dir config))
    (converge-topology system nil nil {} @db)
    (add-watch db :synchronizer (partial converge-topology system))
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
