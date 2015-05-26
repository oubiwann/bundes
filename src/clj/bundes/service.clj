(ns bundes.service
  "Placeholder protocol for services.")

(defprotocol Service
  "A protocol for services."
  (get-id [this] "Unique ID for comparison.")
  (start! [this] "Start the service.")
  (stop! [this] "Stop the service."))
