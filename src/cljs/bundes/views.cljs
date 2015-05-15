(ns ^:figwheel-always bundes.views
    (:require [sablono.core        :as html :refer-macros [html]]
              [cljs.core.async     :as a]
              [clojure.string      :as s]
              [om.core             :as om]
              [om-bootstrap.panel  :as p]
              [om-bootstrap.button :as b]
              [om-bootstrap.nav    :as n]
              [om-bootstrap.modal  :as md]
              [om-bootstrap.random :as r]
              [om-tools.dom        :as d :include-macros true]
              [om-bootstrap.table  :refer [table]])
    (:require-macros [cljs.core.async.macros :refer [go]]))

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
  (reify
    om/IInitState
    (init-state [this]
      {:visible? false
       :triggering? false})
    om/IRenderState
    (render-state [this {:keys [visible? triggering?]}]
      (let [show-modal (fn [_] (om/set-state! owner :visible? true))
            hide-modal (fn [_] (om/set-state! owner :visible? false))
            trigger-action (fn [_]
                             (try
                               (println "triggering!")
                               (go
                                 (let [ch (a/timeout 5000)]
                                   (a/<! ch)
                                   (println "timeout expired!")
                                   (om/set-state! owner :triggering? false)))
                               (om/set-state! owner :visible? false)
                               (om/set-state! owner :triggering? true)
                               (catch :default e
                                 (println "some error occurred:" (pr-str e)))))]
        (d/tr
         (d/td (d/a {:href (str "#/unit/" (name id))} (name id)))
         (d/td (some-> unit :type name))
         (d/td (some-> unit :runtime :type name))
         (d/td
          (when visible?
            (md/modal {:header (d/h4 (str "Trigger one-time run for: " (name id)))
                       :footer (d/div (b/button {:on-click hide-modal} "Cancel")
                                      (b/button {:on-click trigger-action} "Trigger"))
                       :close-button? true
                       :animate? true
                       :visible? visible?}
                      (str "Run a one-off batch for id: " (name id))))
          (b/button-group
           {}
           (b/button {} "Suspend")
           (b/button {:disable? triggering?
                      :bs-style (if triggering? "danger" "info")
                      :on-click show-modal}
                     "Trigger"))))))))

(defn unit-list
  [app owner]
  (reify
    om/IRender
    (render [this]
      (d/div
       (table {:striped? true :bordered? true :hover? true}
              (d/thead (d/tr
                        (d/th "Unit")
                        (d/th "Type")
                        (d/th "Runtime")
                        (d/th "Actions")))
              (d/tbody (om/build-all unit-row (:units app))))))))

(defn daemon-detail
  [data owner]
  (reify om/IRender
    (render [this]
      (d/div
        (p/panel
          {:header (d/h3
                   (str (name (:id data)) "  -  ")
                   (r/label {:bs-style "default"} "Daemon"))}
          (d/p (some-> (:unit data) :description))
          (table {}
            (d/tbody
              (d/tr (d/td "Status")
                    (d/td (some-> (:unit data)
                                   :status
                                   name s/capitalize)))
              (d/tr (d/td "Type")
                    (d/td (some-> (:unit data)
                                   :runtime
                                   :type name s/capitalize)))
              (d/tr (d/td "Resources")
                    (d/td (pr-str (some-> (:unit data) :profile))))
              (d/tr (d/td "Runtime Details")
                    (d/td (let [k (some-> (:unit data)
                                   :runtime
                                   :type
                                   name
                                   keyword)]
                              (pr-str (some-> (:unit data)
                                               :runtime
                                               k)))))))
          (b/button-group {}
            (if (= :start (some-> (:unit data)
                                   :status))
                  (b/button {:bs-style "danger"} "Suspend")
                  (b/button {:bs-style "info"} "Trigger"))))))))

(defn batch-detail
  [data owner]
  (reify om/IRender
    (render [this]
      (d/div
        (p/panel
          {:header (d/h3
                   (str (name (:id data)) "  -  ")
                   (r/label {:bs-style "primary"} "Batch"))}
          (d/p (some-> (:unit data) :description))
          (table {}
            (d/tbody
              (d/tr (d/td "Schedule")
                    (d/td (some-> (:unit data)
                                   :schedule)))
              (d/tr (d/td "Resources")
                    (d/td (pr-str (some-> (:unit data)
                                           :profile))))))
          (b/button-group {}
            (b/button {} "Suspend")
            (b/button {:bs-style "info"} "Trigger")))))))

(defn unit-details
  [app owner]
  (reify om/IRender
    (render [this]
      (let [id   (some-> app :router :params :id keyword)
            unit (some-> app :units id)]
              (if (= :daemon (:type unit))
                    (om/build daemon-detail {:unit unit :id id})
                    (om/build batch-detail {:unit unit :id id}))))))
