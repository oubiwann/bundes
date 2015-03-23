(ns bundes.tick
  (:require [cronj.core            :as c]
            [cronj.data.task       :as t]
            [bundes.effect         :refer [perform-effect]]
            [clojure.tools.logging :refer [debug info]]))

(defn create!
  []
  (let [ticker (c/cronj :entries [])]
    (c/start! ticker)
    ticker))

(defn tick-handler
  [{:keys [id] :as unit}]
  (debug "ticking for:" id)
  (t/task id (fn [_ _] (perform-effect {:action :one-off :unit unit}))))

(defmethod perform-effect :sched-add
  [{:keys [ticker unit sched]}]
  (debug "adding schedule:" sched "for unit: " (:id unit))
  (c/schedule-task ticker (tick-handler unit) sched))

(defmethod perform-effect :sched-del
  [{:keys [ticker unit sched]}]
  (debug "removing schedule:" sched "for unit: " (:id unit))
  (c/unschedule-task ticker (:id unit)))
