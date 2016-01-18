(ns bundes.watch
  "Facade around watchman to interact with the unit
   registry. bundes.unit is not pulled in here, because
   the interaction with the registry goes through
   assoc! and dissoc!"
  (:require [clj-yaml.core         :refer [parse-string]]
            [watch.man             :refer [watch! ->path]]
            [clojure.tools.logging :refer [info]]))

(defn extract-id
  "When faced with an appropriate path, yield the corresponding
   unit ID."
  [{:keys [type path]}]
  (and (= type :path)
       (when-let [[_ id] (re-find #"^([^.].*).ya?ml$" (str path))]
         (keyword id))))

(def load-unit
  "Load a YAML file from disk."
  (comp parse-string slurp str))

(defn load-dir
  "On first run, recurse down a directory and loads appropriate unit."
  [reg dir]
  (let [dir (java.io.File. dir)]
    (doseq [file  (.listFiles dir)
            :when (.isFile file)
            :let  [path (.relativize (.toPath dir) (.toPath file))]]
      (when-let [id (extract-id {:type :path :path path})]
        (when-not (re-find #"/" (name id)) ;; do not recurse
          (assoc! reg id (load-unit (->path (str file) []))))))))

(defn watch-units
  "Watch unit-dir and update registry accordingly."
  [reg dir]
  (load-dir reg dir)
  (watch!
   dir
   (fn [{:keys [type path types] :as ev}]
     (info "configuration change event: " (pr-str ev))
     (when-let [id (extract-id ev)]
       (case (last (remove #{:overflow} (:types ev)))
         :delete (dissoc! reg id)
         :modify (assoc! reg id (load-unit (->path dir path)))
         :create (assoc! reg id (load-unit (->path dir path))))))))
