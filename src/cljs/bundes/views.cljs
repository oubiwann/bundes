(ns ^:figwheel-always bundes.views
  (:require [sablono.core :as html :refer-macros [html]]
            [om.core      :as om]
            [om-bootstrap.button :as b]
            [om-bootstrap.nav :as n]
            [om-bootstrap.table :refer [table]]
            [om-tools.dom :as d :include-macros true]))

(defn with-layout
  "This is a closure over a component which yields a new component.
   The idea is to provide a quick way to generate some kind of constant
   layout before yielding to a per-view component.

   In our case, the layout is composed of a navbar component and the main
   and a standard bootstrap grid to hold the router which will dispatch
   to the appropriate per-view component."
  [component]
  (fn [app owner]
    (reify om/IRender
      (render [this]
        (d/div
         (n/navbar
          {:brand (d/a {:href "#"} "Bundes")}
          (n/nav {:collapsible? true}))

         ;; now hand over to our main component
         (d/div {:class "container"} (om/build component app)))))))

(defn unit-row
  [[id unit] owner]
  (reify om/IRender
    (render [this]
      (d/tr
       (d/td (d/a {:href (str "#/unit/" (name id))} (name id)))
       (d/td (some-> unit :type name))
       (d/td (some-> unit :runtime :type name))))))

(defn unit-list
  [app owner]
  (reify om/IRender
    (render [this]
      (table {:striped? true :bordered? true :hover? true}
             (d/thead (d/tr (d/th "Unit") (d/th "Type") (d/th "Runtime")))
             (apply d/tbody (om/build-all unit-row (:units app)))))))

(defn unit-details
  [app owner]
  (reify om/IRender
    (render [this]
      (html [:h1 (str "I'm the unit details for unit:"
                      (get-in app [:router :params :id]))]))))
