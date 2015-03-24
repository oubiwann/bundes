(ns bundes.tick
  "Wrapper around the cronj scheduler"
  (:require [cronj.core            :as c]
            [cronj.data.task       :as t]
            [bundes.effect         :refer [perform-effect]]
            [clojure.tools.logging :refer [debug info]]))

(defn create!
  "Create, start and yield a scheduler."
  []
  (let [ticker (c/cronj :entries [])]
    (c/start! ticker)
    ticker))

(defn tick-handler
  "When the scheduler ticks for a unit, just perform a new
   side-effect of type :one-off, which will be picked up by mesos"
  [payload unit]
  (let [id      (:id unit)
        payload (assoc (merge payload unit) :action :one-off)]
    (debug "ticking for:" id)
    (t/task id (fn [_ _] (perform-effect payload)))))

(defmethod perform-effect :sched-add
  [{:keys [ticker units] :as payload}]
  (doseq [{:keys [schedule id] :as unit} units]
    (debug "adding schedule:" schedule "for unit: " (:id unit))
    (c/schedule-task ticker (tick-handler payload unit) schedule)))

(defmethod perform-effect :sched-del
  [{:keys [ticker units] :as payload}]
  (doseq [{:keys [schedule id] :as unit} units]
    (debug "removing schedule:" schedule "for unit: " (:id unit))
    (c/unschedule-task ticker id)))
