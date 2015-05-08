(ns ^:figwheel-always bundes.router
  (:require [goog.events            :as events]
            [om.core                :as om]
            [goog.history.EventType :as EventType]
            [secretary.core         :refer [add-route! dispatch!]])
  (:import goog.History))


(defonce history (History.))

(defn init
  [routes app]

  (doseq [[route component] (partition 2 routes)]
    (add-route!
     route
     (fn [params]
       (swap! app assoc :router {:component component
                                 :params params}))))

  (goog.events/listen
   history
   EventType/NAVIGATE
   (fn [event]
     (dispatch!
      (.-token event))))

  (.setEnabled history true)

  (fn [app owner]
    (reify om/IRender
      (render [this]
        (om/build (get-in app [:router :component]) app)))))

(defn redirect
  [location]
  (.setToken history location))
