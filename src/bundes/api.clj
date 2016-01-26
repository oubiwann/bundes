(ns bundes.api
  "HTTP API facade"
  (:require [com.stuartsierra.component :as com]
            [bundes.db                  :as db]
            [spootnik.reporter            :as r]
            [cheshire.core              :as json]
            [bidi.bidi                  :refer [match-route*]]
            [net.http.server            :refer [run-server]]
            [clojure.tools.logging      :refer [info error]]))

(def api-routes
  ["/units" [[""                  {:get :unit-list}]
             [["/" :id "/pause"]  {:put :unit-pause}]
             [["/" :id "/resume"] {:put :unit-resume}]]])

(defn decode-input
  [{:keys [body] :as request}]
  (assoc request :body (json/parse-string body true)))

(defn encode-output
  [{:keys [body] :as request}]
  (-> request
      (update :body json/generate-string)
      (assoc-in [:headers :content-type] "application/json")))

(defn match-route
  [{:keys [uri] :as request}]
  (match-route* api-routes uri request))

(defn response
  [body]
  {:status 200 :body body})

(defmulti dispatch :handler)

(defmethod dispatch :unit-list
  [{:keys [db]}]
  (response {:units (db/list-units db)}))

(defmethod dispatch :unit-pause
  [{:keys [route-params db] :as request}]
  (response {:units (db/pause! db (keyword (:id route-params)))}))

(defmethod dispatch :unit-resume
  [{:keys [route-params db]}]
  (response {:units (db/resume! db (keyword (:id route-params)))}))

(defmethod dispatch :default
  [request]
  {:status 404
   :body {:message "invalid route"}})

(defn handler-fn
  [{:keys [reporter db]}]
  (fn [request]
    (try
      (-> request
          (match-route)
          (decode-input)
          (assoc :db db)
          (dispatch)
          (encode-output))
      (catch Exception e
        (let [{:keys [status message silence?]} (ex-data e)]
          (when-not silence?
            (error e "cannot process HTTP request")
            (r/capture! reporter e))
          {:status  (or status 500)
           :headers {:content-type "application/json"}
           :body    (json/generate-string
                     {:message (or message (.getMessage e))})})))))

(defrecord BundesApi [port options bind-addr db reporter server]
  com/Lifecycle
  (start [this]
    (assoc this :server (run-server (assoc options :port port)
                                    (handler-fn this))))
  (stop [this]
    (server)
    (assoc this :server nil)))

(defn make-api
  ([]
   (map->BundesApi nil))
  ([api]
   (map->BundesApi api)))
