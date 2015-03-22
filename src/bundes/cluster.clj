(ns bundes.cluster
  (:require [bundes.effect         :refer [perform-effect]]
            [clojure.tools.logging :refer [debug info]]))

(defmethod perform-effect :stop
  [{:keys [unit]}]
  (debug "stopping unit " (:id unit)))

(defmethod perform-effect :start
  [{:keys [unit]}]
  (debug "starting unit " (:id unit)))

(defmethod perform-effect :one-off
  [{:keys [unit]}]
  (debug "one-off run for unit " (:id unit)))


(defn cluster
  [])
