(ns bundes.effect
  "This multimethod is used to signal that side-effects need to be performed.
   Two assumptions are made:

   - Effects are maps
   - They contain "
  (:require [schema.core           :as s]
            [clojure.tools.logging :refer [log info warn]]))

(defmulti perform-effect
  "Open protocol for perform-effect.
   Expects maps and dispatches on :action key."
  :action)

(defmethod perform-effect :log
  [{:keys [level message]}]
  (log level message))

(defmethod perform-effect :default
  [{:keys [action] :as effect}]
  (warn "could not find handler for effect" (name action) ":" effect))

(comment
  (def effect-schema
    (let [unit    s/Map
          actions {:sched-add {:ticker    s/Record
                               :framework s/Record
                               :units     [unit]}
                   :sched-del {:ticker    s/Record
                               :units     [unit]}
                   :stop      {:framework s/Record
                               :units     [unit]}
                   :start     {:framework s/Record
                               :units     [unit]}
                   :one-off   {:framework s/Record
                               :units     [unit]}}]
      actions)))
