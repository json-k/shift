![Shift](http://keeber.org/wp-content/uploads/2016/04/shift.png)

A service based workflow tool with a web interface.

##Gradle Build

The Gradle build (controlled by the gradle.properties) uses the following tasks:

 * **serviceRun** - creates the service folder and runs it (port 7777) in this case.
 * **serviceStop** - stops the running service (it does this by calling the embedded admin servlet).
 * **serviceUpdate** - rebuilds the WAR file (the running service will reload it).
 * **serviceDist** - creates a final ZIP file for distribution of the service.

The typical (my) workflow for working on the service is to start it in the IDE, update the code and run the 'serviceUpdate' task to reload it. This is designed to reflect the way the service runs in production.

##Philosophy

A service of this type can be written using standard tools (it runs Embedded Apache) and distributed to a variety of operating systems. The distribution does not require a web-server and can be run with it's own memory requirements and in it's own process. I have been running services of this type for a variety of functions for many years.


