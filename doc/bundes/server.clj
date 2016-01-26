{:mesos     {:master "zk://localhost:2181/mesos"}
 :zookeeper {:conn "zk://localhost:2181/bundes"}
 :http      {:port 8080}
 :unit-dir  "doc/bundes/units/"
 :logging   {:level   :debug
             :console true
             :overrides {"io.netty" "warn"}}}
