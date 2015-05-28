Quickstart Guide
================

Getting up and running with **bundes** involves three things
which we'll cover in this quick walk-through:

- Installing and running `Apache Zookeeper`_.
- Installing and running `Apache Mesos`_.
- Installing and running **bundes**.

.. _Apache Zookeeper: http://zookeeper.apache.org
.. _Apache Mesos: http://mesos.apache.org

Obtaining bundes
----------------

**Bundes** is released in both source and binary.

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

Runtime requirements for **bundes** are kept to a minimum:

- Java 7 Runtime (Sun JDK recommended)
- Apache Mesos (0.21 or above)
- Apache Zookeeper

Build requirements
~~~~~~~~~~~~~~~~~~

If you with to build **bundes** yourself, you will additionally need
the `leiningen`_ build tool to produce working artifacts.

.. _leiningen: http://leiningen.org

Minimal configuration
---------------------

Running bundes
--------------

  
