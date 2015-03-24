(ns bundes.mesos.scheduler
  "The bulk of our interaction with Mesos happens here.
   The scheduler implements two interfaces: ClusterManager
   our simple interface to start and stop tasks and Scheduler from
   mesos.

   The scheduler simply listens for incoming offers and stores them
   in memory. A scheduled job (every 5 second) will process start and
   stop commands."
  (:require [cronj.core            :as c]
            [cronj.data.task       :refer [task]]
            [clojure.tools.logging :refer [debug info]])
  (:import java.util.ArrayList
           org.apache.mesos.Scheduler
           org.apache.mesos.Protos$Filters
           org.apache.mesos.Protos$Resource
           org.apache.mesos.Protos$Value$Type
           org.apache.mesos.Protos$Value$Scalar
           org.apache.mesos.Protos$TaskID
           org.apache.mesos.Protos$TaskInfo
           org.apache.mesos.Protos$TaskState
           org.apache.mesos.Protos$ExecutorID
           org.apache.mesos.Protos$ExecutorInfo
           org.apache.mesos.Protos$CommandInfo
           org.apache.mesos.Protos$ContainerInfo))



(defn scalar-resource
  "Utility function to create a scalar resource."
  [type val]
  (-> (Protos$Resource/newBuilder)
      (.setName type)
      (.setType Protos$Value$Type/SCALAR)
      (.setScalar (-> (Protos$Value$Scalar/newBuilder)
                      (.setValue val)
                      (.build)))
      (.build)))

(defn show-resources
  [{:keys [resources]}]
  (pr-str (reduce merge {} (map (juxt (comp #(.getValue %) key)
                                      (comp :resources val))
                                resources))))

(defn resource-map
  "Given an offer, yield a tuple of offer ID to resource map."
  [offer]
  (let [resources (seq (.getResourcesList offer))
        scalar?   (fn [r] (= (.getType r) Protos$Value$Type/SCALAR))
        tuple     (fn [r] [(keyword (.getName r))
                           (-> r .getScalar .getValue)])]
    [(-> offer .getId)
     {:resources (reduce merge {} (map tuple (filter scalar? resources)))
      :offer     offer}]))



(defn create!
  "Create an instance of Scheduler and ClusterManager.
   Updates from mesos get stored in an atom which is queried
   during start! and stop! events."
  [state]
  (reify
    org.apache.mesos.Scheduler
    (registered [this driver framework-id master-info]
      (swap! state assoc :master-info master-info)
      (info "registered with id:" (.getValue framework-id)))

    (reregistered [this driver master-info]
      (swap! state assoc :master-info master-info)
      (info "registered"))

    (disconnected [this driver]
      (swap! state dissoc :master-info)
      (info "disconnected"))

    (resourceOffers [this driver offers]
      (debug "got resources offers, previous:" (show-resources @state))
      (let [new-resources (reduce merge {} (map resource-map (seq offers)))]
        (swap! state update-in [:resources] merge new-resources))
      (debug "updated resource map: " (show-resources @state)))

    (offerRescinded [this driver offer-id]
      (debug "offer rescinded:" (.getValue offer-id))
      (swap! state update-in [:resources] dissoc offer-id)
      (debug "updated resource map: " (show-resources @state)))

    (statusUpdate [this driver status]
      (debug "status update: task"
             (-> status .getTaskId .getValue)
             "is in state:"
             (-> status .getState .getValueDescriptor .getName))

      (when (= (-> status .getState) Protos$TaskState/TASK_FINISHED)
        (debug "task finished"))

      (when (#{Protos$TaskState/TASK_LOST
               Protos$TaskState/TASK_FAILED
               Protos$TaskState/TASK_KILLED}
             (-> status .getState))
        (debug "task error")
        ;; TODO: update state map accordingly
        ))

    (frameworkMessage [this driver executor-id slave-id data]
      (debug "framework message"))

    (slaveLost [this driver slave-id]
      (debug "slave lost"))

    (executorLost [this driver executor-id slave-id status]
      (debug "executor lost"))

    (error [this driver message]
      (debug "error: " message))))
