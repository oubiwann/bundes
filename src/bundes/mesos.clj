(ns bundes.mesos
  "Namespace which implements mesos related side-effects"
  (:require [mesomatic.async.scheduler :as async]
            [mesomatic.scheduler       :as s]
            [mesomatic.types           :as t]
            [mesomatic.allocator       :refer [allocate-naively]]
            [bundes.effect             :refer [perform-effect]]
            [clojure.core.async        :refer [chan <! go] :as a]
            [clojure.tools.logging     :refer [debug info error]]))

(defmethod perform-effect :stop
  [{:keys [mesos units]}]
  (info "asked to start units")
  (a/put! mesos {:type :stop :units units}))

(defmethod perform-effect :start
  [{:keys [mesos units]}]
  (info "asked to stop units")
  (a/put! mesos {:type :start :units units}))

(defmethod perform-effect :one-off
  [{:keys [mesos unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "one-off run for unit " (:id unit))
  (a/put! mesos {:type :start :units [unit]}))

(defn profile->resources
  [{:keys [mem cpus] :as profile}]
  [{:type :value-scalar :name "mem"  :scalar (or mem 128.0)}
   {:type :value-scalar :name "cpus" :scalar (or cpus 0.2)}])

(defn runtime->port-ranges
  [{:keys [type docker]}]
  (when (and (= type :docker) (:port-mappings docker))
    [{:type   :value-ranges
      :name   "ports"
      :ranges [{:begin 0
                :end   (dec (count (:port-mappings docker)))}]}]))

(defn runtime->container
  [{:keys [type docker] :as runtime}]
  (when (= type :docker)
    {:type   :container-type-docker
     :docker (assoc docker :type :container-type-docker)}))

(defn runtime->command
  [{:keys [type command docker] :as runtime}]
  (cond
    (= type :command) {:value command :shell true}
    (= type :docker)  {:shell false}))

(defn unit->task-info
  "Convert a bundesrat unit into a suitable Mesos TaskInfo"
  [{:keys [runtime profile id] :as unit}]
  {:name      id
   :task-id   {:value id}
   :resources (profile->resources profile)
   :container (runtime->container runtime)
   :command   (runtime->command runtime)
   :count     (or (:count profile) 1)
   :maxcol    (or (:maxco profile) 1)})

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
  [{:keys [driver offers] :as state} {:keys [units] :as payload}]
  (info "trying to start" (count units) "units")
  (if-let [tasks (allocate-naively offers (map unit->task-info units))]
    (doseq [[offer-id tasks] (group-by :offer-id tasks)
            :let [tasks (mapv t/map->TaskInfo tasks)]]
      (info "now starting tasks on offer" (pr-str offer-id) ":" (pr-str tasks))
      (s/launch-tasks! driver offer-id tasks))
    (error "no match found for workload" (pr-str units))
    )
  state)

(defmethod update-state :resource-offers
  [state payload]
  (info "updating resource offers with" (count (:offers payload)) "new offers")
  (assoc state :offers (:offers payload)))

(defmethod update-state :stop
  [state payload]
  (info "framework stopped")
  state)

(defmethod update-state :offer-rescinded
  [state payload]
  (info "offer rescinded: " (pr-str payload))
  state)

(defmethod update-state :status-update
  [state payload]
  (info "status update: " (pr-str payload))
  state)

(defmethod update-state :default
  [state payload]
  (info "unhandled message: " (pr-str payload))
  state)

(defn framework!
  "Start a mesos framework"
  [config]
  (let [master    (:mesos config)
        input     (chan 10)
        sched     (async/scheduler input)
        framework (t/map->FrameworkInfo {:user ""
                                         :name "Bundesrat Framework"
                                         :principal "bundesrat-framework"})
        driver    (s/scheduler-driver sched framework master)]
    (s/start! driver)
    (a/reduce update-state {:driver driver} input)
    input))
