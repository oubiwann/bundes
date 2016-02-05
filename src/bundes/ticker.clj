(ns bundes.ticker
  "Wrapper around the cronj scheduler"
  (:require [com.stuartsierra.component :as com]
            [cronj.core                 :as c]
            [cronj.data.task            :as t]
            [bundes.effect              :refer [perform-effect]]
            [clojure.tools.logging      :refer [debug info]]))

(defprotocol Scheduler
  (schedule! [this framework unit])
  (unschedule! [this unit]))

(defn create!
  "Create, start and yield a scheduler."
  []
  (let [cronj (c/cronj :entries [])]
    (c/start! cronj)
    cronj))

(defn tick-handler
  "When the scheduler ticks for a unit, just perform a new
   side-effect of type :one-off, which will be picked up by mesos"
  [framework reporter unit]
  (let [id      (:id unit)
        payload {:unit      unit
                 :framework framework
                 :reporter  reporter
                 :action    :one-off}]
    (t/task id (fn [_ _] (perform-effect payload)))))

(defmethod perform-effect :sched-add
  [{:keys [ticker framework units]}]
  (doseq [unit units]
    (schedule! ticker framework unit)))

(defmethod perform-effect :sched-del
  [{:keys [ticker units]}]
  (doseq [unit units]
    (unschedule! ticker unit)))

(defrecord Ticker [cronj reporter]
  com/Lifecycle
  (start [this]
    (info "starting scheduler")
    (assoc this :cronj (create!)))
  (stop [this]
    (c/stop! cronj)
    (assoc this :cronj nil))
  Scheduler
  (schedule! [this framework unit]
    (let [handler (tick-handler framework reporter unit)]
      (info "adding schedule:" (:schedule unit) "for unit:" (:id unit))
      (c/schedule-task cronj handler (:schedule unit))))
  (unschedule! [this unit]
    (let [{:keys [id]} unit]
      (info "removing schedule for unit:" id)
      (c/unschedule-task cronj id))))

(defn make-ticker
  []
  (map->Ticker {}))
