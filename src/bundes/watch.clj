(ns bundes.watch
  (:require [bundes.unit           :as unit]
            [clj-yaml.core         :refer [parse-string]]
            [org.spootnik.watchman :refer [watch! ->path]]
            [clojure.tools.logging :refer [info]]))

(defn extract-id
  [{:keys [type path]}]
  (and (= type :path)
       (if-let [[_ id] (re-find #"^([^.].*).ya?ml$" (str path))] (keyword id))))

(def load-unit
  (comp parse-string slurp str))

(defn load-dir
  [reg dir]
  (let [dir (java.io.File. dir)]
    (doseq [file  (.listFiles dir)
            :when (.isFile file)
            :let  [path (.relativize (.toPath dir) (.toPath file))]]
      (when-let [id (extract-id {:type :path :path path})]
        (when-not (re-find #"/" (name id)) ;; do not recurse
          (assoc! reg id (load-unit (->path (str file) []))))))))

(defn watch-units
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
