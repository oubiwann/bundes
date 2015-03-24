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
           org.apache.mesos.Protos$ContainerInfo$DockerInfo$PortMapping
           org.apache.mesos.Protos$Status))


(defprotocol ClusterManager
  "Exposed interface with mesos, used by bundes.mesos to
   to implement relevant side-effects for perform-effect."
  (start! [this unit])
  (stop! [this unit]))

(defn random-uuid
  []
  (str
   (java.util.UUID/randomUUID)))

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

(defn docker-port-mappings
  [builder port-mappings]
  (doseq [{:keys [host container protocol]} port-mappings]
    (.addPortMappings
     builder
     (-> (Protos$ContainerInfo$DockerInfo$PortMapping/newBuilder)
         (.setHostPort (int host))
         (.setContainerPort (int container))
         (cond-> protocol (.setProtocol protocol))
         (.build))))
  builder)

(defn docker-info
  [{:keys [image port-mappings] :as docker}]
  (-> (Protos$ContainerInfo$DockerInfo/newBuilder)
      (.setImage image)
      (cond-> port-mappings (docker-port-mappings port-mappings))
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
  [cpus mem {:keys [resources offer]}]
  (debug "matching" (pr-str {:cpus cpus :mem mem}) "against" (pr-str resources))
  (when (and (<= cpus (:cpus resources))
             (<= mem (:mem resources)))
    offer))

(defn adapt-offer
  "We requested "
  [cpus mem offer]
  (-> offer
      (update-in [:resources :cpus] (- cpus))
      (update-in [:resources :mem] (- mem))))

(defn ->task
  "Create and launch a TaskInfo"
  [slave-id unit]
  (debug "found offer, will launch task")
  (let [uuid    (random-uuid)
        id      (format "bundesrat-task-%s-%s" (:id unit) uuid)
        mem     (or (some-> unit :runtime :mem) 512)
        cpus    (or (some-> unit :runtime :cpus) 1)
        task-id (-> (Protos$TaskID/newBuilder)
                    (.setValue uuid)
                    (.build))]
    {:id   (:id unit)
     :task (-> (Protos$TaskInfo/newBuilder)
               (.setName id)
               (.setTaskId task-id)
               (.setSlaveId slave-id)
               (.addResources (sched/scalar-resource "mem" mem))
               (.addResources (sched/scalar-resource "cpus" cpus))
               (add-unit unit)
               (.build))}))

(defn dispatch-task
  "Find and appropriate offer for our task."
  [{:keys [resources startq heldq] :as acc} {:keys [runtime] :as unit}]
  (let [cpus          (or (:cpus runtime) 1)
        mem           (or (:mem runtime) 512)
        [small big]   (split-with (partial offer-matches? cpus mem) resources)
        [offer & big] big]
    (if offer
      (-> acc
          (assoc :resources (concat small [(adapt-offer cpus mem offer)] big))
          (update-in [:startq (.getId offer)] conj
                     (->task (-> offer :offer .getSlaveId) unit)))
      (update-in acc [:heldq] conj unit))))

(defn mk-filters
  []
  (-> (Protos$Filters/newBuilder) (.setRefuseSeconds 1) (.build)))

(defn kill-task
  [state driver id task-id]
  (.killTask driver task-id)
  (swap! state update-in [:running] dissoc id))

(defn run-framework!
  [master]
  (let [state     (atom {:running {} :runq []})
        scheduler (sched/create! state)
        framework (build-framework-info)
        driver    (MesosSchedulerDriver. scheduler framework master)]

    (future
      (info "running mesos driver")
      (let [status (.run driver)]
        (info "mesos scheduler exited with status:" (driver-statuses status))
        (.stop driver)
        (System/exit (if (= Protos$Status/DRIVER_STOPPED status) 0 1))))

    (info "mesos scheduler has been started, yielding back to main thread")

    (reify
      ;; Both cluster manager operation are either executed
      ;; immediately or get queued for later execution.
      ClusterManager
      (start! [this units]
        (let [{:keys [resources] :as snapshot} @state]
          (let [init    {:resources resources :startq [] :heldq []}
                res     (reduce dispatch-task init units)
                filters (mk-filters)]
            (debug "got dispatch results" (select-keys res [:startq :heldq])
                   (sched/show-resources res))
            (doseq [{:keys [offer-id tasks]} (:startq res)]
              (.launchTasks driver [offer-id] (map :task tasks) filters)
              (doseq [{:keys [id task]} tasks]
                (swap! state assoc-in [:running id] (.getTaskId task)))))))

      (stop! [this units]
        (doseq [unit units]
          (debug "stopping task" (:id unit))
          (if-let [task-id (some-> @state :running (get (:id unit)))]
            (kill-task state driver (:id unit) task-id)
            (debug "id not found, unit not running")))))))
