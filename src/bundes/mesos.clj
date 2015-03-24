(ns bundes.mesos
  "Namespace which implements mesos related side-effects"
  (:require [bundes.effect          :refer [perform-effect]]
            [bundes.mesos.framework :refer [start! stop! run-framework!]]
            [clojure.tools.logging  :refer [debug info]]))

(defmethod perform-effect :stop
  [{:keys [mesos units]}]
  (stop! mesos units))

(defmethod perform-effect :start
  [{:keys [mesos units]}]
  (start! mesos units))

(defmethod perform-effect :one-off
  [{:keys [mesos unit]}]
  (when-not (= :batch (:type unit))
    (throw (ex-info "invalid unit type" {:unit (:id unit)})))
  (debug "one-off run for unit " (:id unit))
  (start! mesos [unit]))

(defn framework!
  "Start a mesos framework"
  [config]
  (run-framework! (:mesos config)))
