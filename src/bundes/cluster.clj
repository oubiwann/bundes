(ns bundes.cluster
  (:require [bundes.effect         :refer [perform-effect]]
            [clojure.tools.logging :refer [debug info]]))

(defmethod perform-effect :stop
  [{:keys [unit]}]
  (when-not (= :daemon (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "stopping unit " (:id unit)))

(defmethod perform-effect :start
  [{:keys [unit]}]
  (when-not (= :daemon (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "starting unit " (:id unit)))

(defmethod perform-effect :one-off
  [{:keys [unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "one-off run for unit " (:id unit)))


(defn cluster
  [])
