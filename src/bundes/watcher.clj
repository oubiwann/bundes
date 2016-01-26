(ns bundes.watcher
  "Facade around watchman to interact with the unit
   database."
  (:require [com.stuartsierra.component :as com]
            [clojure.edn                :as edn]
            [bundes.db                  :refer [set-unit! del-unit!]]
            [watch.man                  :refer [watch! ->path close]]
            [clojure.tools.logging      :refer [info]]))

(defn extract-id
  "When faced with an appropriate path, yield the corresponding
   unit ID."
  [{:keys [type path]}]
  (and (= type :path)
       (when-let [[_ id] (re-find #"^([^.].*).clj$" (str path))]
         (keyword id))))

(def load-unit
  "Load a YAML file from disk."
  (comp edn/read-string slurp str))

(defn load-dir
  "On first run, recurse down a directory and loads appropriate unit."
  [db dir]
  (info "loading dir")
  (let [dir (java.io.File. dir)]
    (doseq [file  (.listFiles dir)
            :when (.isFile file)
            :let  [path (.relativize (.toPath dir) (.toPath file))]]
      (info "trying: " (str file))
      (when-let [id (extract-id {:type :path :path path})]
        (when-not (re-find #"/" (name id)) ;; do not recurse
          (info "found unit: " (str file) (name id))
          (set-unit! db id (load-unit (->path (str file) []))))))))

(defn on-change-fn
  "Watch unit-dir and update db accordingly."
  [dir db]
  (fn [{:keys [type path types] :as ev}]
    (info "configuration change event: " (pr-str ev))
    (when-let [id (extract-id ev)]
      (case (last (remove #{:overflow} (:types ev)))
        :delete (del-unit! db id)
        :modify (set-unit! db id (load-unit (->path dir path)))
        :create (set-unit! db id (load-unit (->path dir path)))))))

(defrecord Watcher [server db directory]
  com/Lifecycle
  (start [this]
    (load-dir db directory)
    (assoc this :server (watch! directory (on-change-fn directory db))))
  (stop [this]
    (close server)
    (assoc this :server nil)))

(defn make-watcher
  ([unit-dir]
   (map->Watcher {:directory unit-dir})))
