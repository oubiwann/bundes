REST API
========

Bundes exposes a simple REST API to inspect
the status of each unit.

API Details
-----------

Authorization & Authentication
  No authorization or authentication is supported
  yet. It is encourage to protect access to the
  API through the use of client SSL certificate.

Serialization
  The only input and output serialization method supported
  is **JSON**. 

Operations Overview
-------------------

.. list-table:: Operations
   :header-rows: 1
   :widths: 5 50

  * - Operation
    - Action
  * - `GET /v1/units`_
    - Lists units
  * - `PUT /v1/units/:id/suspend`_
    - Suspend unit execution or scheduling
  * - `PUT /v1/units/:id/unsuspend`_
    - Resume unit execution or scheduling
  * - `PUT /v1/units/:id`_
    - One-off run for unit


.. GET /units:

GET /v1/units
-------------

Returns a list of all known units

Sample Request::

  GET /v1/units HTTP/1.1
  Host: localhost

Sample Response

.. sourcecode:: javascript

                
  {
    "units": [
      {
        "web-frontend": {
          {
            "type": "daemon",
            "profile": {
              "mem": 512.0,
              "cpus": 0.5,
              "count": 4,
              "maxcol": 2
            },
            "runtime": {
              "type": "docker",
              "docker": {
                "image": "dockerfile/nginx",
                "port-mappings": [
                   {"container-port": 80}
                ]
              }
            },
            "id": "web-frontend",
            "status": "start"
          }
        },
        "sanity-check": {
            "type": "batch",
            "schedule": "/20 * * * * * *",
            "profile": {
              "mem": 256.0,
              "cpus": 1.0
            },
            "runtime": {
              "type": "command",
              "command": "/srv/cluster/foobar"
            },
            "id": "sanity-check",
            "status": "start"
          }
        }
    ]
  }

.. PUT /v1/units/:id/suspend:

PUT /v1/units/:id/suspend
-------------------------

Suspends unit execution or scheduling.


Sample Request::

  PUT /v1/units/web-frontend/suspend
  Host: localhost

.. PUT /v1/units/:id/unsuspend:

PUT /v1/units/:id/unsuspend
---------------------------

Resumes unit execution or scheduling.

Sample Request::

  PUT /v1/units/web-frontend/unsuspend
  Host: localhost

.. PUT /v1/units/:id:

PUT /v1/units/:id
-----------------

Schedules a one-off run for a batch job.
This is only valid for batch jobs, since long
running tasks / daemons can only be started or stopped.

Sample Request::

  PUT /v1/units/sanity-check
  Host: localhost


