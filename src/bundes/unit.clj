(ns bundes.unit
  "A unit registry")

(defprotocol UnitRegistry
  "Custom protocol to implement
   status toggling for units."
  (suspend [this id])
  (unsuspend [this id]))

(defn ->unit
  "Normalizes input data into a proper unit.
   XXX: It wouldn't hurt to enforce a schema here."
  [id unit]
  (-> unit
      (assoc :id id)
      (update-in [:type] keyword)
      (update-in [:status] #(if % (keyword %) :start))
      (update-in [:runtime :type] keyword)))

(defn atom-registry
  "Make our state atom act as a transient for convenience
   and implement our custom UnitRegistry protocol."
  [state]
  (reify
    UnitRegistry
    (suspend [this id]
      (swap! state assoc-in [id :status] :stop))
    (unsuspend [this id]
      (swap! state assoc-in [id :status] :start))
    clojure.lang.IDeref
    (deref [this]
      @state)
    clojure.lang.ITransientMap
    (assoc [this key val]
      (swap! state assoc key (->unit key val))
      this)
    (without [this key]
      (swap! state dissoc key)
      this)))
