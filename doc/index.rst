Bundes: warrants order in the confederatio
==========================================

.. image:: _static/bundesrat.jpg
   :alt: bundesrat logo
   :align: center

**Bundes** schedules and orchestrates **long running daemons** or **batch jobs**
throughout your infrastructure. To do this, it relies on `Apache Mesos`_ and
acts as a mesos **framework**.

Scalability
  **Bundes** treats your infrastructure as a uniform pool of computing resources
  and schedules **daemons** and **batches** according to your resource constraints.

Compatibility  
  To **Bundes**, workloads may either be plain commands to start or
  docker images.

Simplicity
  **Bundes** provides insights into its current state through its REST API
  or web interface, allowing you to quickly adjust resources or trigger
  job execution.

Coherency
  By relying on plain YAML_ file to describe units of work, **bundes**
  allows you to stick to your standard way of supplying configuration,
  using your configuration management framework of choice (Puppet_, Chef_
  and Ansible_ are popular options.) Logging also leverages your standard
  logging infrastructure and plays well with popular central logging options
  like Logstash_.

Resilient
  Any number of **bundes** processes may run concurrently. A leader process
  will be elected to handle orchestration duties.

Lightweight
  The **bundes** process is a small and self-contained **JAR** with low
  memory requirements. Its only requirement are `Apache Mesos`_ and
  `Apache Zookeeper`_.

**Bundes** is sponsored by Exoscale_.

    **Bundes** is short for Bundesrat, the Swiss German name for the
    7-member Swiss federal council which acts as head of state.

.. _Apache Mesos: http://mesos.apache.org
.. _Apache Zookeeper: http://zookeeper.apache.org
.. _Puppet: http://puppetlabs.com
.. _Chef: http://getchef.com
.. _Ansible: http://ansible.com
.. _Logstash: http://logstash.net
.. _YAML: http://yaml.org
.. _Exoscale: https://exoscale.ch
  
Contents:

.. toctree::
   :maxdepth: 2

   quickstart
   concepts
   administrator
   api
   
