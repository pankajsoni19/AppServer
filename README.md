AppServer
=========

Examples of web-servers in action.

Online_application:
=========

HTTP/s server written in java, with Cassandra as backend and data format as Json, as application center

The project is a home grown demo for a simple http/https server written entirely in java.

This has examples for smtp mail sending, talking to cassandra in CQL V3, working on oracle jdk7 http/s server at: http://docs.oracle.com/javase/7/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html

The settings are in conf folder and it uses logger to log comments.

Main class is in bootup.java (there is graceful shutdown, if needed).

As this is a demo for server load capability, I have not updated all the html files with all menu options, neither condensed them.

Any applications here, have not been tested in actual production environment, the developer or company will not be liable for any damage caused by their use.
