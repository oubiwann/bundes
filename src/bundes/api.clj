(ns bundes.api
  (:require [bundes.unit          :as u]
            [compojure.route      :as route]
            [ring.middleware.json :as json]
            [ring.util.response   :refer [response status redirect]]
            [qbits.jet.server     :refer [run-jetty]]
            [compojure.core       :refer [GET PUT routes]]))

(defn header
  [resp header val]
  (let [strval (if (keyword? val) (name val) (str val))]
    (assoc-in resp [:headers header] strval)))

(defn api-routes
  [reg]
  (->
   (routes
    (GET "/units" []
         (response @reg))
    (PUT "/units/:id/suspend" [id]
         (response (u/suspend reg (keyword id))))
    (PUT "/units/:id/unsuspend" [id]
         (response (u/unsuspend reg (keyword id))))
    (PUT "/units/:id" [id]
         (response))
    (route/not-found "<html><h2>404</h2></html>"))
   (json/wrap-json-body)
   (json/wrap-json-response)))

(defn start!
  [config reg]
  (run-jetty (assoc config :ring-handler (api-routes reg))))
