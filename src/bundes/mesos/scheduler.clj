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

(defprotocol ClusterManager
  "Exposed interface with mesos, used by bundes.mesos to
   to implement relevant side-effects for perform-effect."
  (start! [this unit])
  (stop! [this unit]))

(defn add-unit
  "Given a TaskInfo, add relevant details based on chosen
   runtime."
  [builder {:keys [runtime] :as unit}]
  (if (= :docker (:type runtime))
    (.setContainerInfo builder (-> (Protos$ContainerInfo/newBuilder)
                                   (.setImage (:image runtime))
                                   (.build)))
    (.setCommandInfo builder (-> (Protos$CommandInfo/newBuilder)
                                 (.setValue (:command runtime))
                                 (.build)))))

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

(defn resource-map
  "Given an offer, yield a tuple of offer ID to resource map."
  [offer]
  (let [resources (seq (.getResourcesList offer))
        scalar?   (fn [r] (= (.getType r) Protos$Value$Type/SCALAR))
        tuple     (fn [r] [(keyword (.getName r))
                           (-> r .getScalar .getValue)])]
    [(-> offer .getOfferID .getValue)
     {:resources (reduce merge {} (map tuple (filter scalar? resources)))
      :offer     offer}]))

(defn offer-matches?
  "Make sure an offer can satisfy our request."
  [cpu mem [id [{:keys [resources offer]}]]]
  (when (and (<= cpu (:cpus resources))
             (<= mem (:mem resources)))
    offer))

(defn dispatch-task
  "Find and appropriate offer for our task."
  [{:keys [resources] :as state} {:keys [runtime] :as unit}]
  (let [cpu        (or (:cpu runtime) 1)
        mem        (or (:mem runtime) 512)]
    (some (partial offer-matches? cpu mem) resources)))

(defn launch-task
  "Create and launch a TaskInfo"
  [state driver offer unit]
  (let [counter (:counter (swap! state update-in [:counter] inc))
        id      (format "bundesrat-task-%s-%s" (:id unit) counter)
        mem     (or (some-> unit :runtime :mem) 512)
        cpus    (or (some-> unit :runtime :cpus) 1)
        filters (-> (Protos$Filters/newBuilder)
                    (.setRefuseSeconds 1)
                    (.build))
        task-id (-> (Protos$TaskID/newBuilder)
                    (.setValue (str counter))
                    (.build))
        task    (-> (Protos$TaskInfo/newBuilder)
                    (.setName id)
                    (.setTaskId task-id)
                    (.setSlaveId (.getSlaveId offer))
                    (.addResources (scalar-resource "mem" mem))
                    (.addResources (scalar-resource "cpus" cpus))
                    (add-unit unit)
                    (.build))
        tasks    (doto (ArrayList.) (.add task))]
    (.launchTasks driver (.getId offer) tasks filters)))

(defn create!
  "Create an instance of Scheduler and ClusterManager.
   Updates from mesos get stored in an atom which is queried
   during start! and stop! events."
  []
  (let [state (atom {:counter 0 :running {}})]
    (reify
      ;; Both cluster manager operation are either executed
      ;; immediately or get queued for later execution.
      ClusterManager
      (start! [this unit]
        (let [{:keys [driver resources]} @state]
          (when driver
            (debug "dispatching task" (:id unit))
            (when-let [offer (dispatch-task resources unit)]
              (launch-task driver offer unit)
              (swap! state update-in [:running] assoc (:id unit) offer)))))

      (stop! [this unit]
        (let [{:keys [driver]} @state]
          (when driver
            (debug "stopping task" (:id unit))
            (when-let [id (some-> @state :running (get (:id unit)) .getValue)]
              (.killTask driver id)
              (swap! state update-in [:running] dissoc (:id unit))))))


      org.apache.mesos.Scheduler
      (registered [this driver framework-id master-info]
        (swap! state assoc :driver driver :master-info master-info)
        (info "registered with id:" (.getValue framework-id)))

      (reregistered [this driver master-info]
        (swap! state assoc :driver driver :master-info master-info)
        (info "registered"))

      (disconnected [this driver]
        (swap! state dissoc :driver :master-info)
        (info "disconnected"))

      (resourceOffers [this driver offers]
        (debug "got resources offers, previous:" (pr-str (:resources @state)))
        (let [new-resources (reduce merge {} (map resource-map (seq offers)))]
          (swap! state update-in [:resources] merge new-resources))
        (debug "updated resource map: " (pr-str (:resources @state))))

      (offerRescinded [this driver offer-id]
        (debug "offer rescinded:" (.getValue offer-id))
        (swap! state update-in [:resources] dissoc (.getValue offer-id))
        (debug "updated resource map: " (pr-str (:resources @state))))

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
          ;; XXX: this may be a bit radical
          (.abort driver)))

      (frameworkMessage [this driver executor-id slave-id data]
        (debug "framework message"))

      (slaveLost [this driver slave-id]
        (debug "slave lost"))

      (executorLost [this driver executor-id slave-id status]
        (debug "executor lost"))

      (error [this driver message]
        (debug "error: " message)))))
