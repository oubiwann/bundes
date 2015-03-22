(ns bundes.mesos.framework
  (:gen-class)
  (:require [clojure.tools.logging  :refer [info debug]]
            [bundes.mesos.scheduler :as sched])
  (:import java.io.File
           org.apache.mesos.MesosSchedulerDriver
           org.apache.mesos.Protos$FrameworkInfo
           org.apache.mesos.Protos$ExecutorID
           org.apache.mesos.Protos$ExecutorInfo
           org.apache.mesos.Protos$CommandInfo
           org.apache.mesos.Protos$Status))

(defn build-executor-info
  [uri]
  (-> (Protos$ExecutorInfo/newBuilder)
      (.setExecutorId (-> (Protos$ExecutorID/newBuilder)
                          (.setValue "default")))
      (.setCommand (-> (Protos$CommandInfo/newBuilder)
                       (.setValue uri)))
      (.setName "Bundesrat Executor (clojure)")
      (.setSource "clojure_test")
      (.build)))

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

(defn run-framework
  [master]
  (let [uri       (.getCanonicalPath (File. "./bundes-executor"))
        executor  (build-executor-info uri)
        framework (build-framework-info)
        scheduler (sched/create executor)
        driver    (MesosSchedulerDriver. scheduler framework master)
        status    (.run driver)]
    (info "mesos scheduler exited with status:" (driver-statuses status))
    (.stop driver)
    ;; (System/exit (if (= Protos$Status/DRIVER_STOPPED status) 0 1))
    ))
