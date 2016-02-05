(ns bundes.engine
  (:require [com.stuartsierra.component :as com]
            [bundes.db                  :as db]
            [bundes.effect              :refer [perform-effect]]
            [bundes.decisions           :refer [decisions]]
            [clojure.tools.logging      :refer [info]]))

(defn process!
  [engine])

(defprotocol AsyncEngine
  (converge! [this _ _ old new]))

(defrecord Engine [reporter ticker framework db]
  com/Lifecycle
  (start [this]
    (db/set-watch db :synchronizer (partial converge! this))
    this)
  (stop [this]
    (db/del-watch db :synchronizer)
    this)
  AsyncEngine
  (converge! [this _ _ old new]
    (let [side-effects (decisions (:units old) (:units new))]
      (info "the world has changed, converging!")
      (doseq [effect side-effects]
        (perform-effect (merge effect {:ticker    ticker
                                       :framework framework
                                       :reporter  reporter}))))))

(defn make-engine
  []
  (map->Engine {}))
