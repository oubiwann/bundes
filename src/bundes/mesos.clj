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
     :docker (assoc docker :network :docker-network-bridge)}))

(defn runtime->command
  [{:keys [type command docker] :as runtime}]
  (cond
    (= type :command) {:shell true :value command}
    (= type :docker)  {:shell false}
    :else             (throw (ex-info "invalid unit" {:type type}))))

(defn unit->task-info
  "Convert a bundesrat unit into a suitable Mesos TaskInfo"
  [{:keys [runtime profile id] :as unit}]
  {:name      (format "bundesrat-task-%s-0" (name id))
   :task-id   {:value (str (java.util.UUID/randomUUID))}
   :resources (concat (profile->resources profile)
                      (runtime->port-ranges runtime))
   :container (runtime->container runtime)
   :command   (runtime->command runtime)
   :count     (or (:count profile) 1)
   :maxcol    (or (:maxcol profile) 1)})

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
  (if-let [tasks (allocate-naively offers (map unit->task-info units))]
    (doseq [[offer-id tasks] (group-by :offer-id tasks)]
      (info "starting tasks on offer" (-> offer-id :value) ":" (pr-str tasks))
      (s/launch-tasks! driver offer-id tasks))
    (error "no match found for workload" (pr-str units)))
  state)

(defmethod update-state :resource-offers
  [state payload]
  (info "updating offers with" (count (:offers payload)) "new offers")
  (debug "offers: " (pr-str (:offers payload)))
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
        framework {:user "" :name "bundesrat framework"}
        driver    (s/scheduler-driver sched framework master)]
    (s/start! driver)
    (a/reduce update-state {:driver driver} input)
    input))
