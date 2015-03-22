(ns bundes.unit)

(defprotocol UnitRegistry
  (suspend [this id])
  (unsuspend [this id]))

(defn ->Unit
  [id unit]
  (-> unit
      (assoc :id id)
      (update-in [:type] keyword)
      (update-in [:status] #(if % (keyword %) :start))
      (update-in [:runtime :type] keyword)))

(defn atom-registry
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
      (swap! state assoc key (->Unit key val))
      this)
    (without [this key]
      (swap! state dissoc key)
      this)))
