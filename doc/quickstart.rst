Quickstart Guide
================

Getting up and running with bundes involves three things
which we'll cover in this quick walk-through:

- Installing and running `Apache Zookeeper`_.
- Installing and running `Apache Mesos`_.
- Installing and running bundes.

.. _Apache Zookeeper: http://zookeeper.apache.org
.. _Apache Mesos: http://mesos.apache.org

Obtaining bundes
----------------

Bundes is released in both source and binary.

Binary releases
  Binary releases are the simplest way to get started and are hosted on
  github: https://github.com/pyr/bundes/releases.

Each release contains:

- A source code archive.
- A standard standalone build (**bundes-VERSION-standalone.jar**).
- A debian package which comes with the standalone build.

Requirements
------------

Runtime requirements
~~~~~~~~~~~~~~~~~~~~

Runtime requirements for bundes are kept to a minimum:

- Java 7 Runtime (Sun JDK recommended)
- Apache Mesos (0.21 or above)
- Apache Zookeeper

Build requirements
~~~~~~~~~~~~~~~~~~

If you with to build bundes yourself, you will additionally need
the `leiningen`_ build tool to produce working artifacts.

.. _leiningen: http://leiningen.org

Installing Zookeeper and Mesos
------------------------------

This is best done by following the guide at
https://docs.mesosphere.com/getting-started/datacenter/install/

Installing Bundes
-----------------

Once installed, bundes can be run and will load its configuration
from ``/etc/bundes``.

Minimal configuration
~~~~~~~~~~~~~~~~~~~~~

.. sourcecode:: yaml

   ---
   cluster:
     hosts: "localhost:2181"
     prefix: "/bundes"
   mesos: "zk://localhost:2181/mesos"
   service:
     port: 8080
   unit-dir: "/etc/bundes/units"
   logging:
     level: "info"
     console: true
     overrides:
       bundes: "debug"

With this configuration, bundes will expect to find
work units described in the ``/etc/bundes/units`` directory.

Refer to the :ref:`Administrator Guide` for a complete description
of the unit description syntax. You can provide the following
scheduled task to ensure bundes is functional in ``/etc/bundes/units/logfoo.yml``


.. sourcecode:: yaml

  ---
  type: "batch"
  schedule: "/10 * * * * * *"
  profile:
    mem: 32
    cpus: 0.1
  runtime:
    type: "command"
    command: "logger foo"


Running bundes
~~~~~~~~~~~~~~

With this configuration installed, bundes can be run directly::

  java -jar bundes-VERSION-standalone.jar

The web interface will be served on HTTP port 8080.  

  
