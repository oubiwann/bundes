(ns bundes.api
  "HTTP API facade"
  (:require [bundes.unit          :as u]
            [compojure.route      :as route]
            [ring.middleware.json :as json]
            [ring.util.response   :refer [response status redirect]]
            [qbits.jet.server     :refer [run-jetty]]
            [compojure.core       :refer [GET PUT routes]]))

(defn api-routes
  "Build a ring handler around a unit registry"
  [reg]
  (->
   (routes
    (GET "/units" []                 (response @reg))
    (PUT "/units/:id/suspend" [id]   (response (u/suspend reg (keyword id))))
    (PUT "/units/:id/unsuspend" [id] (response (u/unsuspend reg (keyword id))))
    (PUT "/units/:id" [id]           (response))
    (route/resources                 "/")
    (route/not-found                 "<html><h2>404</h2></html>"))
   (json/wrap-json-body)
   (json/wrap-json-response)))

(defn start!
  "Run the ring-handler with jetty"
  [config reg]
  (run-jetty (assoc config :ring-handler (api-routes reg))))
