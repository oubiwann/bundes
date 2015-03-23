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

(defn run-framework!
  [master]
  (let [scheduler (sched/create!)
        framework (build-framework-info)
        driver    (MesosSchedulerDriver. scheduler framework master)
        status    (.run driver)]

    (future
      (let [status (.run driver)]
        (info "mesos scheduler exited with status:" (driver-statuses status))
        ;; (System/exit (if (= Protos$Status/DRIVER_STOPPED status) 0 1))
        (.stop driver)))

    scheduler))
