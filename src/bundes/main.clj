(ns bundes.main
  "Bundes provides orchestration for batch jobs and long running
   process across several machines, on top of Apache Mesos. Batch
   and Daemon workloads are express as \"units\", simple definitions.

   For details and the full rationale behind bundes, please see
   https://github.com/pyr/bundes.

   This namespace is the program's entry point. Reads configuration
   and sets a component system.
   See https://github.com/stuartsierra/component
   for a detailed explanation of the component library.

   Functionaly wise, bundes sets up a graph of components
   interacting with each other, see `make-system` for details
   on the dependency specifications. The components are:

   - `watcher`, which listens to filesystem events to
     build a database of units.
   - `api` which exposes a way to inspect unit states
   - `db` holds a database of units and signals when changes occur.
   - `engine` based on database changes, makes decisions on which
      side-effect to perform.
   - `ticker` scheduling facilities each tick for a scheduled job
      is just a request for a side-effect
   - `framework`

"
  (:gen-class)
  (:require [clojure.edn                :as edn]
            [com.stuartsierra.component :as com]
            [schema.core                :as s]
            [bundes.db                  :as db]
            [bundes.framework           :as framework]
            [bundes.api                 :as api]
            [bundes.watcher             :as watcher]
            [bundes.ticker              :as ticker]
            [spootnik.reporter          :as reporter]
            [bundes.engine              :as engine]
            [signal.handler             :refer [with-handler]]
            [clojure.tools.logging      :refer [info debug error]]
            [unilog.config              :refer [start-logging!]]
            [clojure.tools.cli          :refer [cli]]))

(def config-schema
  {:logging                   s/Any
   :http                      {:port                     s/Num
                               (s/optional-key :options) s/Any}
   :unit-dir                  s/Str
   :zookeeper                 {:conn s/Str}
   :mesos                     {:master s/Str}
   (s/optional-key :reporter) reporter/config-schema})

(defn read-config
  "Loads an EDN configuration. No post-processing but
   exits on errors."
  [path]
  (try
    (let [validator (partial s/validate config-schema)]
      (-> (or path (System/getenv "BUNDES_CONFIGURATION") "/etc/bundes/server.clj")
          slurp
          edn/read-string
          validator))
    (catch Exception o_O
      (binding [*out* *err*]
        (println "Could not read configuration: " (.getMessage o_O))
        (System/exit 1)))))

(defn get-cli
  "Parse command line arguments, exits on error."
  [args]
  (try
    (cli args
         ["-h" "--help"  "Show Help"           :default false :flag true]
         ["-f" "--path"  "Configuration file"  :default nil])
    (catch Exception o_O
      (binding [*out* *err*]
        (println "Could not parse arguments: " (.getMessage o_O))
        (System/exit 1)))))



(defn make-system
  [{:keys [unit-dir mesos http reporter zookeeper]}]
  (com/system-using
   (com/system-map
    :api       (api/make-api http)
    :watcher   (watcher/make-watcher unit-dir)
    :db        (db/make-db)
    :ticker    (ticker/make-ticker)
    :framework (framework/make-framework mesos)
    :engine    (engine/make-engine)
    :reporter  (reporter/make-reporter reporter))
   {:api       [:reporter :db]
    :watcher   [:reporter :db]
    :db        [:reporter]
    :engine    [:reporter :db :ticker :framework]
    :ticker    [:reporter]
    :framework [:reporter :db]}))

(defn -main
  "Executable entry point, parse options, reads config and
   starts execution."
  [& args]
  (let [debug?             (= (System/getProperty "bundes.debug") "true")
        [opts args banner] (get-cli args)
        help?              (:help opts)]

    (when help?
      (println banner)
      (System/exit 0))

    (let [config (read-config (:path opts))
          system (atom (make-system config))]

      (start-logging! (:logging config))
      (swap! system com/start-system)

      (try
        (with-handler :term
          (info "caught SIGTERM, quitting")
          (swap! system com/stop-system)
          (info "all components shut down")
          (System/exit 0))

        (with-handler :hup
          (info "caught SIGHUP, quitting")
          (swap! system com/stop-system)
          (info "all components shut down")
          (System/exit 0))

        (reporter/instrument! (:reporter @system) [:bundes])
        (framework/process! (:framework @system))


        (info "main task finished, stopping system")
        (swap! system com/stop-system)
        (System/exit 0)

        (catch Throwable t
          (error t)
          (if debug?
            (throw t)
            (System/exit 1)))))))
