(ns bundes.framework
  (:gen-class)
  (:require [bundes.scheduler :as sched])
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

(defn -main
  [& [ip master]]
  (let [uri       (-> (File. "./bundes-executor") .getCanonicalPath)
        executor  (build-executor-info uri)
        framework (build-framework-info)
        scheduler (sched/create executor)
        driver    (MesosSchedulerDriver. scheduler framework master)
        status    (.run driver)]

    (.stop driver)
    (System/exit (if (= Protos$Status/DRIVER_STOPPED status) 0 1))))
