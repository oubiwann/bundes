(ns bundes.cluster
  "Simple facade for interaction with Zookeeper through Apache Curator"
  (:require [clojure.tools.logging :refer [info]]
            [bundes.service        :refer [get-id start! stop!]])
  (:import org.apache.curator.retry.ExponentialBackoffRetry
           org.apache.curator.framework.CuratorFramework
           org.apache.curator.framework.CuratorFrameworkFactory
           org.apache.curator.framework.imps.CuratorFrameworkState
           org.apache.curator.framework.recipes.leader.LeaderSelector
           org.apache.curator.framework.recipes.leader.LeaderSelectorListener
           org.apache.curator.framework.recipes.leader.CancelLeadershipException
           org.apache.curator.framework.recipes.leader.LeaderLatch
           org.apache.curator.framework.state.ConnectionState
           org.apache.curator.framework.api.CuratorWatcher
           java.util.concurrent.TimeUnit))

(defn curator-client
  "Create a zookeeper client"
  [{:keys [hosts retry-policy conn-timeout sess-timeout namespace]
    :or   {retry-policy (ExponentialBackoffRetry. 1000 10)
           conn-timeout 500
           sess-timeout 40000
           namespace "bundes"}}]
  (-> (doto (CuratorFrameworkFactory/builder)
        (.connectString hosts)
        (.retryPolicy retry-policy)
        (.connectionTimeoutMs conn-timeout)
        (.sessionTimeoutMs sess-timeout)
        (.namespace namespace))
      (.build)))

(defn listener
  [readyp service]
  (reify
    LeaderSelectorListener
    (takeLeadership [this framework]
      (info "taking leadership for this election")
      (start! service)
      (info "relinquishing leadership"))
    (stateChanged [this framework new-state]
      (when (#{ConnectionState/SUSPENDED
               ConnectionState/LOST}
             new-state)
        (try (stop! service)
             (catch Exception e
               (throw (CancelLeadershipException. e)))
             (finally
               (throw (CancelLeadershipException.))))))))

(defn leader-selector
  "Given a client, a zookeeper path and a candidate honoring the `Candidate`
   protocol, run for office !"
  [client readyp path service]
  (doto (LeaderSelector. client path (listener readyp service))
    (.setId (get-id service))
    (.autoRequeue)))

(defn run-election
  [cluster service]
  (let [client   (curator-client cluster)
        prefix   (or (:prefix cluster) "/bundes")
        readyp   (promise)
        selector (leader-selector client readyp prefix service)]
    (.start client)
    (.blockUntilConnected client)
    (.start selector)
    (info "started election process")
    @readyp))
