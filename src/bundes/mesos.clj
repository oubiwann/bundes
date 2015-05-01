(ns bundes.mesos
  "Namespace which implements mesos related side-effects"
  (:require [mesomatic.async.scheduler :as async]
            [mesomatic.scheduler       :as s]
            [mesomatic.types           :as t]
            [mesomatic.allocator       :refer [allocate-naively]]
            [bundes.effect             :refer [perform-effect]]
            [clojure.core.async        :refer [chan <! go]]
            [clojure.tools.logging     :refer [debug info]]))

(defmethod perform-effect :stop
  [{:keys [mesos units]}]
  (info "asked to start units")
  (put! mesos {:type :stop :units units}))

(defmethod perform-effect :start
  [{:keys [mesos units]}]
  (info "asked to stop units")
  (put! mesos {:type :start :units units}))

(defmethod perform-effect :one-off
  [{:keys [mesos unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "one-off run for unit " (:id unit))
  (put! mesos {:type :start :units [unit]}))

(defn unit->task-info
  "Convert a bundesrat unit into a suitable Mesos TaskInfo"
  [unit]
  unit)

(defmulti update-state (comp :type last vector))

(defmethod update-state :start
  [{:keys [driver offers] :as state} {:keys [units] :as payload}]
  (info "start: " (pr-str payload))
  (when-let [tasks (allocate-naively offers (map unit->task-info units))]
    (doseq [[offer-id tasks] (group-by :offer-id tasks)]
      (launch-tasks! driver offer-id tasks)))
  state)

(defmethod update-state :resource-offers
  [state payload]
  (info "resource offers: " (pr-str payload))
  state)

(defmethod update-state :stop
  [{:keys [driver]} {:keys [units]}]
  (info "stop!!")
  state)

(defmethod update-state :offer-rescinded
  [{:keys [offers] :as state} payload]
  (info "offer rescinded!" (pr-str payload))
  state)

(defmethod update-state :status-update
  [{:keys [offers] :as state} payload]
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
    (go (async/reduce update-state {:driver driver} inut))
    input))
