(ns bundes.mesos.scheduler
  (:require [clojure.tools.logging :refer [debug info]])
  (:import java.util.ArrayList
           org.apache.mesos.Scheduler
           org.apache.mesos.Protos$Filters
           org.apache.mesos.Protos$Resource
           org.apache.mesos.Protos$Value$Type
           org.apache.mesos.Protos$Value$Scalar
           org.apache.mesos.Protos$TaskID
           org.apache.mesos.Protos$TaskInfo
           org.apache.mesos.Protos$TaskState))

(def launched (atom 0))

(def incremental-id (atom 0))

(defn resource-map
  [offer]
  (let [resources (seq (.getResourcesList offer))
        scalar?   (fn [r] (= (.getType r) Protos$Value$Type/SCALAR))
        tuple     (fn [r] [(keyword (.getName r))
                           (-> r .getScalar .getValue)])]
    (reduce merge {} (map tuple (filter scalar? resources)))))

(defn create
  [executor-info]
  (reify org.apache.mesos.Scheduler
    (registered [this driver framework-id master-info]
      (println "registered with id "
               (.getValue framework-id)))
    (reregistered [this driver master-info]
      (println "registered"))
    (disconnected [this driver]
      (println "disconnected"))
    (resourceOffers [this driver offers]
      (println "got resource offers!")
      (let [cpus 1
            mem  512]
        (doseq [offer (seq offers)]
          (let [resources (resource-map offer)]
            (println "got resource map: " (pr-str resources))
            (println "currently launched: " @launched)
            (when (and (>= (:cpus resources) cpus)
                       (>= (:mem resources) mem)
                       (zero? @launched))
              (println "found matching offer with ID " (-> offer .getId .getValue))
              (let [filters (-> (Protos$Filters/newBuilder)
                                (.setRefuseSeconds 1)
                                (.build))
                    task-id (-> (Protos$TaskID/newBuilder)
                                (.setValue (str (swap! incremental-id inc)))
                                (.build))
                    tasks (ArrayList.)
                    task  (-> (Protos$TaskInfo/newBuilder)
                              (.setName (str "bundesrat task " @incremental-id) )
                              (.setTaskId task-id)
                              (.setSlaveId (.getSlaveId offer))
                              (.addResources (-> (Protos$Resource/newBuilder)
                                                 (.setName "mem")
                                                 (.setType Protos$Value$Type/SCALAR)
                                                 (.setScalar (-> (Protos$Value$Scalar/newBuilder)
                                                                 (.setValue mem)))))
                              (.addResources (-> (Protos$Resource/newBuilder)
                                                 (.setName "cpus")
                                                 (.setType Protos$Value$Type/SCALAR)
                                                 (.setScalar (-> (Protos$Value$Scalar/newBuilder)
                                                                 (.setValue cpus)))))
                              (.setExecutor executor-info)
                              (.build))]
                (.add tasks task)
                (.launchTasks driver (.getId offer) tasks filters)
                (swap! launched inc)))))))
    (offerRescinded [this driver offer-id])
    (statusUpdate [this driver status]
      (println "status update: task "
               (-> status .getTaskId .getValue)
               "is in state"
               (-> status .getState .getValueDescriptor .getName))

      (when (= (-> status .getState) Protos$TaskState/TASK_FINISHED)
        (println "task finished")
        (swap! launched dec))
      (when (#{Protos$TaskState/TASK_LOST
               Protos$TaskState/TASK_FAILED
               Protos$TaskState/TASK_KILLED}
             (-> status .getState))
        (println "task error")
        (.abort driver)))
    (frameworkMessage [this driver executor-id slave-id data]
      (println "framework message"))
    (slaveLost [this driver slave-id]
      (println "slave lost"))
    (executorLost [this driver executor-id slave-id status]
      (println "executor lost"))
    (error [this driver message]
      (println "error: " message))))
