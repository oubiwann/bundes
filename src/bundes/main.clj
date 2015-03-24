(ns bundes.main
  "Our main entry point. Reads configuration
   and sets up vital functions:

   - Watch configured unit directory for changes
   - Start HTTP API.
   - Update configuration map based on events in
     the unit directory and HTTP API commands.
   - Start a scheduler for batch units.
   - Register a mesos framework.

   We use a couple of methods to ensure as much decoupling as possible
   between components:

   - Unit configuration is done in stored in a clojure atom.
   - The atom is watched through add-watch.
   - Interaction with the scheduler and framework is done
     through a multimethod."
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
  "Loads a YAML configuration. No post-processing but
   exits on errors."
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

(defn converge-topology
  "The expected state of the world, held in our atom, has changed.
   Run a diff of both state which will yield a list of side effects
   to be performed.

   Run through that list"
  [system _ _ old new]
  (let [side-effects (decisions old new)]
    (info "the world has changed, converging!")
    (doseq [effect side-effects]
      (perform-effect (merge system effect)))))

(defn start!
  "This is the crux of the namespace, where everything gets started
   and glued together."
  [config]
  (info "bundesrat: starting up.")
  (let [db      (atom {})                            ;; 1. Hold config in atom
        reg     (unit/atom-registry db)              ;; 2. Mimick a transient
        system  {:ticker (tick/create!)              ;; 3. Start scheduler
                 :mesos  (mesos/framework! config)}] ;; 4. Register with mesos
    (watch/watch-units reg (:unit-dir config))       ;; 5. Watch unit dir
    (converge-topology system nil nil {} @db)        ;; 6. First converge
    (add-watch db :synchronizer                      ;; 7. Watch for changes
               (partial converge-topology system))   ;;
    (api/start! (:service config) reg)))             ;; 8. Start HTTP API

(defn -main
  "Executable entry point, parse options, reads config and
   starts execution."
  [& args]
  (let [[opts args banner] (get-cli args)
        config             (read-config (:path opts))
        help?              (:help opts)]

    (start-logging! (:logging config))

    (when help?
      (println banner)
      (System/exit 0))

    (start! config)))
