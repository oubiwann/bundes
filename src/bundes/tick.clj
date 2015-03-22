(ns bundes.tick
  (:require [cronj.core            :as c]
            [cronj.data.task       :as t]
            [bundes.effect         :refer [perform-effect]]
            [clojure.tools.logging :refer [debug info]]))

(def cnj
  "Our scheduler instance"
  (delay (c/cronj :entries [])))

(defn start-ticker!
  []
  (c/start! @cnj))

(defn tick-handler
  [{:keys [id] :as unit}]
  (debug "ticking for:" id)
  (t/task id (fn [_ _] (perform-effect {:action :one-off :unit unit}))))

(defmethod perform-effect :sched-add
  [{:keys [unit sched]}]
  (debug "adding schedule:" sched "for unit: " (:id unit))
  (c/schedule-task @cnj (tick-handler unit) sched))

(defmethod perform-effect :sched-del
  [{:keys [unit sched]}]
  (debug "removing schedule:" sched "for unit: " (:id unit))
  (c/unschedule-task @cnj (:id unit)))
