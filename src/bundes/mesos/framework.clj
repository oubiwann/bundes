(ns bundes.mesos.framework
  "Simple facade to create and register a mesos framework.
   The bulk of the functionality lies in the scheduler
   namespace."
  (:require [clojure.tools.logging  :refer [info debug]]
            [bundes.mesos.scheduler :as sched])
  (:import java.io.File
           java.util.ArrayList
           org.apache.mesos.MesosSchedulerDriver
           org.apache.mesos.Protos$FrameworkInfo
           org.apache.mesos.Protos$ExecutorID
           org.apache.mesos.Protos$ExecutorInfo
           org.apache.mesos.Protos$Filters
           org.apache.mesos.Protos$Resource
           org.apache.mesos.Protos$Value$Type
           org.apache.mesos.Protos$Value$Scalar
           org.apache.mesos.Protos$TaskID
           org.apache.mesos.Protos$TaskInfo
           org.apache.mesos.Protos$TaskState
           org.apache.mesos.Protos$CommandInfo
           org.apache.mesos.Protos$ContainerInfo
           org.apache.mesos.Protos$ContainerInfo$Type
           org.apache.mesos.Protos$ContainerInfo$DockerInfo
           org.apache.mesos.Protos$Status))

(defprotocol ClusterManager
  "Exposed interface with mesos, used by bundes.mesos to
   to implement relevant side-effects for perform-effect."
  (start! [this unit])
  (stop! [this unit]))

(defn build-framework-info
  []
  (-> (Protos$FrameworkInfo/newBuilder)
      (.setUser "")
      (.setName "Bundesrat Framework (clojure)")
      (.setPrincipal "bundesrat-framework-clojure")
      (.build)))

(def driver-statuses
  {Protos$Status/DRIVER_NOT_STARTED "DRIVER_NOT_STARTED"
   Protos$Status/DRIVER_RUNNING     "DRIVER_RUNNING"
   Protos$Status/DRIVER_ABORTED     "DRIVER_ABORTED"
   Protos$Status/DRIVER_STOPPED     "DRIVER_STOPPED"})

(defn docker-info
  [{:keys [image] :as docker}]
  (-> (Protos$ContainerInfo$DockerInfo/newBuilder)
      (.setImage image)
      (.build)))

(defn add-unit
  "Given a TaskInfo, add relevant details based on chosen
   runtime."
  [builder {:keys [runtime] :as unit}]
  (if (= :docker (:type runtime))
    (-> builder
        (.setCommand (-> (Protos$CommandInfo/newBuilder)
                         (.setShell false)
                         (.build)))
        (.setContainer (-> (Protos$ContainerInfo/newBuilder)
                           (.setType Protos$ContainerInfo$Type/DOCKER)
                           (.setDocker (docker-info (:docker runtime)))
                           (.build))))
    (.setCommand builder (-> (Protos$CommandInfo/newBuilder)
                             (.setValue (:command runtime))
                             (.build)))))

(defn offer-matches?
  "Make sure an offer can satisfy our request."
  [cpus mem [id {:keys [resources offer]}]]
  (debug "matching" (pr-str {:cpus cpus :mem mem}) "against" (pr-str resources))
  (when (and (<= cpus (:cpus resources))
             (<= mem (:mem resources)))
    offer))

(defn dispatch-task
  "Find and appropriate offer for our task."
  [resources {:keys [runtime] :as unit}]
  (let [cpus (or (:cpus runtime) 1)
        mem  (or (:mem runtime) 512)]
    (some (partial offer-matches? cpus mem) resources)))

(defn launch-task
  "Create and launch a TaskInfo"
  [state driver offer unit]
  (debug "found offer, will launch task")
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
                    (.addResources (sched/scalar-resource "mem" mem))
                    (.addResources (sched/scalar-resource "cpus" cpus))
                    (add-unit unit)
                    (.build))
        tasks    (doto (ArrayList.) (.add task))]
    (.launchTasks driver (.getId offer) tasks filters)
    (swap! state update-in [:running] assoc (:id unit) task-id)
    (swap! state update-in [:resources] dissoc (-> offer .getId))))


(defn run-framework!
  [master]
  (let [state     (atom {:counter 0 :running {}})
        scheduler (sched/create! state)
        framework (build-framework-info)
        driver    (MesosSchedulerDriver. scheduler framework master)]

    (future
      (info "running mesos driver")
      (let [status (.run driver)]
        (info "mesos scheduler exited with status:" (driver-statuses status))
        ;; (System/exit (if (= Protos$Status/DRIVER_STOPPED status) 0 1))
        (.stop driver)))
    (info "mesos scheduler has been started, yielding back to main thread")

    (reify
      ;; Both cluster manager operation are either executed
      ;; immediately or get queued for later execution.
      ClusterManager
      (start! [this unit]
        (debug "in start for unit:" (pr-str unit))
        (let [{:keys [resources] :as snapshot} @state]
          (debug "dispatching task" (:id unit))
          (if-let [offer (dispatch-task resources unit)]
            (launch-task state driver offer unit)
            (debug "no offer found!" (pr-str {:unit unit :resources (sched/show-resources snapshot)})))))
      (stop! [this unit]
        (debug "stopping task" (:id unit))
        (if-let [task-id (some-> @state :running (get (:id unit)))]
          (do
            (debug "found id!")
            (.killTask driver task-id)
            (swap! state update-in [:running] dissoc (:id unit)))
          (debug "id not found, unit not running"))))))
