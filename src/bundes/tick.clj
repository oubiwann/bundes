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
  [payload]
  (let [id (-> payload :unit :id)]
    (debug "ticking for:" id)
    (t/task id (fn [_ _] (perform-effect (assoc payload :action :one-off))))))

(defmethod perform-effect :sched-add
  [{:keys [ticker unit sched] :as payload}]
  (debug "adding schedule:" sched "for unit: " (:id unit))
  (c/schedule-task ticker (tick-handler payload) sched))

(defmethod perform-effect :sched-del
  [{:keys [ticker unit sched]}]
  (debug "removing schedule:" sched "for unit: " (:id unit))
  (c/unschedule-task ticker (:id unit)))
