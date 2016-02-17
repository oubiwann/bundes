(ns bundes.framework
  "Namespace which implements mesos related side-effects"
  (:require [com.stuartsierra.component :as com]
            [mesomatic.async.scheduler  :as async]
            [mesomatic.scheduler        :as sched]
            [mesomatic.types            :as t]
            [bundes.db                  :as db]
            [mesomatic.allocator        :refer [allocate-naively]]
            [bundes.effect              :refer [perform-effect]]
            [clojure.core.async         :refer [chan <! go] :as a]
            [clojure.tools.logging      :refer [debug info error]]))

(defn adapt-retry
  "Increase retry times"
  [n]
  (if n (* n 2) 2000))

(defmethod perform-effect :stop
  [{:keys [framework units]}]
  (info "asked to stop units" (mapv :id units))
  (a/put! (:input framework) {:type :stop :units units}))

(defmethod perform-effect :start
  [{:keys [framework units]}]
  (info "asked to start units" (mapv :id units))
  (a/put! (:input framework) {:type :start :units units}))

(defmethod perform-effect :one-off
  [{:keys [framework unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (info "one-off run for unit " (:id unit))
  (a/put! (:input framework) {:type :start :units [unit]}))

(defn profile->resources
  [{:keys [mem cpus] :as profile}]
  [{:type :value-scalar :name "mem"  :scalar (or mem 128.0)}
   {:type :value-scalar :name "cpus" :scalar (or cpus 0.2)}])

(defn runtime->port-ranges
  [{:keys [port-mappings]}]
  (when (and (= type :docker) port-mappings)
    [{:type   :value-ranges
      :name   "ports"
      :ranges [{:begin 0
                :end   (dec (count port-mappings))}]}]))

(defn runtime->container
  [{:keys [type docker] :as runtime}]
  (when (= type :docker)
    {:type   :container-type-docker
     :docker (assoc docker :network :docker-network-bridge)}))

(defn map->env
  [env]
  (when (map? env)
    {:variables (vec (for [[k v] env] {:name (name k) :value (str v)}))}))

(defmulti runtime->command :type)

(defmethod runtime->command :command
  [{:keys [command environment]}]
  {:shell true :value command :environment (map->env environment)})

(defmethod runtime->command :docker
  [_]
  {:shell false})

(defmethod runtime->command :default
  [unit]
  (throw (ex-info (str "invalid unit type: " (:type unit)) {:unit unit})))

(defn unit->task-info
  "Convert a bundesrat unit into a suitable Mesos TaskInfo"
  [{:keys [runtime profile id instances colocation environment] :as unit}]
  {:name      (format "bundesrat-task-%s-0" (name id))
   :unit-id   id
   :task-id   (:tasks unit)
   :resources (concat (profile->resources profile)
                      (runtime->port-ranges runtime))
   :command   (runtime->command runtime)
   :container (runtime->container runtime)
   :count     (or instances 1)
   :maxcol    (or colocation 1)})

(defmulti update-state (comp :type last vector))

(defmethod update-state :registered
  [state payload]
  (info "framework registered")
  state)

(defmethod update-state :reregistered
  [state payload]
  (info "framework reregistered")
  state)

(defmethod update-state :disconnected
  [state payload]
  (info "framework disconnected")
  state)

(defmethod update-state :error
  [state payload]
  (error "got error on framework:" (pr-str payload))
  state)

(defmethod update-state :start
  [{:keys [framework db driver offers] :as state} {:keys [retry units] :as payload}]
  (let [tinfos (map unit->task-info units)]
    (if-let [tasks (allocate-naively offers tinfos)]
      (doseq [[offer-id tasks] (group-by :offer-id tasks)]
        (info "starting" (count tasks) "tasks on offer" (-> offer-id :value))
        (doseq [{:keys [task-id] :as task} tasks]
          ;; You can view this as a sort of optimistic update
          (db/set-task! db task-id {:state :starting}))
        (sched/launch-tasks! driver offer-id tasks))
      (a/go
        (let [retry (or retry 2000)]
          (error "no match found for units:" (mapv :id units))
          (a/<! (a/timeout retry))
          (a/>! (:input framework) (update payload :retry adapt-retry))))))
  state)

(defmethod update-state :resource-offers
  [state payload]
  (info "updating offers with" (count (:offers payload)) "new offers")
  (assoc state :offers (:offers payload)))

(defmethod update-state :stop
  [state payload]
  (info "framework stopped")
  (doseq [unit (:units payload)]
    (info "will now stop unit" (:id unit))
    (doseq [task (:tasks unit)]
      (info "killing task" (:value task))
      (sched/kill-task! (:driver state) task)))
  state)

(defmethod update-state :offer-rescinded
  [state payload]
  (info "offer rescinded: " (pr-str payload))
  state)

(defmethod update-state :status-update
  [{:keys [db] :as state} {:keys [status]}]
  (info "task" (some-> status :task-id :value) "changed state to" (some-> status :state))
  (let [task-id  (t/map->TaskID (:task-id status))]
    (db/set-task! db task-id (-> status (dissoc :uuid) (dissoc :data)))
    state))

(defmethod update-state :default
  [state payload]
  (info "unhandled message: " (pr-str payload))
  state)

(def framework-info
  {:user "root"
   :name "bundesrat framework"})

(defrecord MesosFramework [master reporter db input sched driver offers]
  com/Lifecycle
  (start [this]
    (let [input  (chan 10)
          sched  (async/scheduler input)
          driver (sched/scheduler-driver sched framework-info master)]
      (debug "created scheduler driver")
      (sched/start! driver)
      (debug "started scheduler driver")
      (assoc this :db db :input input :sched sched :driver driver :offers [])))
  (stop [this]
    (a/close! input)
    (sched/stop! driver)
    (assoc this :input nil :sched nil :driver nil :offers nil)))

(defn process!
  [framework]
  (debug "consuming messages from mesos framework")
  (a/reduce update-state framework (:input framework))
  (sched/join! (:driver framework))
  (debug "dropped out of reduce loop, exiting."))

(defn make-framework
  [config]
  (map->MesosFramework config))

(defn make-task-id
  [id]
  (t/map->TaskID {:value id}))
