(ns bundes.db
  (:import java.util.UUID)
  (:require [com.stuartsierra.component :as com]
            [schema.core                :as s]
            [mesomatic.types            :as t]
            [clojure.tools.logging      :refer [warn info debug]]))

(def unit-common-runtime-schema
  {:type                         (s/enum :docker :command)
   (s/optional-key :environment) {(s/either s/Keyword s/Str) s/Str}})

(def unit-runtime-schema
  (s/if (comp (partial = :command) :type)
    (merge unit-common-runtime-schema {:command s/Str})
    (merge unit-common-runtime-schema
           {:image                          s/Str
            (s/optional-key :port-mappings) s/Any})))

(def unit-common-schema
  "Common schema"
  {:id                      (s/either s/Keyword s/Str)
   :type                    (s/enum :batch :daemon)
   :runtime                 unit-runtime-schema
   (s/optional-key :status) (s/enum :start :stop)
   :profile                 {:mem s/Num :cpus s/Num}})

(def unit-validator
  (s/validator
   (s/if (comp (partial = :batch) :type)
     (merge unit-common-schema
            {:schedule   s/Str})
     (merge unit-common-schema
            {:instances  s/Num
             :colocation s/Num}))))

(defn ->task-ids
  "XXX: I'm not super happy about pulling mesomatic.types here"
  [id instances]
  (vec
   (for [i (range instances) :let [uuid (str (UUID/randomUUID))]]
     (t/->TaskID (format "%s-%03d-%s" (name id) i uuid)))))

(defn ->unit
  "Normalizes input data into a proper unit."
  [id {:keys [instances] :or {instances 1} :as unit}]
  (try
    (assoc (unit-validator (assoc unit :id id))
           :tasks (->task-ids id instances))
    (catch Exception e (warn e "invalid unit") e)))

(defn unit-expander
  [task-db]
  (fn [res k {:keys [tasks] :as unit}]
    (let [tasks (for [t tasks] (get task-db t {:state :unknown}))]
      (assoc res k (assoc unit :tasks (vec tasks))))))

(defn unit-references
  [{:keys [tasks id]}]
  (into {} (for [t tasks] [t {:unit id :state :unknown}])))

(defn add-unit
  "When we add an unit to the world, we need to add it to the
   units key, but also have backreferences to the unit from the
   tasks."
  [world unit]
  (-> world
      (assoc-in [:units (:id unit)] unit)
      (assoc :tasks (merge (:tasks world) (unit-references unit)))))

(defprotocol DatabaseOperationHandler
  (set-watch  [this alias f])
  (del-watch  [this alias])

  (set-unit!  [this id v])
  (del-unit!  [this id])
  (set-task!  [this id v])
  (del-task!  [this id])
  (list-units [this])

  (pause!     [this id])
  (resume!    [this id]))

(defrecord UnitDatabase [reporter world]
  com/Lifecycle
  (start [this]
    (assoc this :world (atom {:units {} :tasks {}})))
  (stop [this]
    (assoc this :world nil))
  DatabaseOperationHandler
  (set-watch [this alias f]
    (assert (keyword? alias))
    (add-watch world alias f))
  (del-watch [this alias]
    (remove-watch world alias))
  (list-units [this]
    (let [{:keys [units tasks]}  @world]
      (reduce-kv (unit-expander tasks) {} units)))
  (set-task! [this id v]
    (swap! world update-in [:tasks id] merge v))
  (set-unit! [this id unit-desc]
    (let [unit-or-e (->unit id unit-desc)]
      (if (instance? Exception unit-or-e)
        (warn "invalid unit description" id ":" (.getMessage unit-or-e))
        (swap! world add-unit unit-or-e))))
  (del-unit! [this id]
    (swap! world update :units dissoc id))
  (del-task! [this id]
    (swap! world update :tasks dissoc id))
  (pause! [this id]
    (swap! world assoc-in [:units id :status] :stop))
  (resume! [this id]
    (swap! world assoc-in [:units id :status] :start)))

(defn make-db
  []
  (map->UnitDatabase {}))
