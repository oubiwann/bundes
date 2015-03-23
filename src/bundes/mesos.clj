(ns bundes.mesos
  "Namespace which implements mesos related side-effects"
  (:require [bundes.effect          :refer [perform-effect]]
            [bundes.mesos.scheduler :refer [start! stop!]]
            [bundes.mesos.framework :refer [run-framework!]]
            [clojure.tools.logging  :refer [debug info]]))

(defmethod perform-effect :stop
  [{:keys [mesos unit]}]
  (when-not (= :daemon (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "stopping unit " (:id unit))
  (stop! mesos unit))

(defmethod perform-effect :start
  [{:keys [mesos unit]}]
  (when-not (= :daemon (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "starting unit " (:id unit))
  (start! mesos unit))

(defmethod perform-effect :one-off
  [{:keys [mesos unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "one-off run for unit " (:id unit))
  (start! mesos unit))

(defn framework!
  "Start a mesos framework"
  [config]
  (run-framework! (:mesos config)))
