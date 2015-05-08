(ns ^:figwheel-always bundes.client
    (:require[om.core       :as om]
             [om.dom        :as dom]
             [bundes.router :as router]
             [bundes.views  :as views]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(def routes
  "Applicate route-map"
  ["/"         views/unit-list
   "/unit/:id" views/unit-details])

(defonce app-state
  (atom {}))

(let [router (router/init routes app-state)
      target {:target (. js/document (getElementById "app"))}]
  (om/root (views/with-layout router)
           app-state
           target))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your applicationp
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
