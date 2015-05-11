(ns ^:figwheel-always bundes.client
    (:require[om.core       :as om]
             [om.dom        :as dom]
             [bundes.router :as router]
             [bundes.views  :as views]))

(enable-console-print!)

(def routes
  "Applicate route-map"
  ["/"         views/unit-list
   "/unit/:id" views/unit-details])

(defonce app-state
  (atom {:units {:console
                 {:type :daemon,
                  :profile {:mem 512.0, :cpus 0.5, :count 2, :maxcol 2},
                  :runtime
                  {:type :docker,
                   :docker
                   {:image "dockerfile/nginx", :port-mappings [{:container-port 80}]}},
                  :id :console,
                  :status :start},
                 :open-metering
                 {:type :batch,
                  :schedule "/20 * * * * * *",
                  :profile {:mem 256, :cpus 1.0},
                  :runtime {:type :command, :command "/srv/cluster/foobar"},
                  :id :open-metering,

                  :status :start}}}))

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
