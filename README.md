# Weld Probe Client Adapter

[![Maven Central](http://img.shields.io/maven-central/v/org.jboss.weld/weld-probe-client-adapter.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22weld-probe-client-adapter%22)
[![License](https://img.shields.io/badge/license-Apache%20License%202.0-yellow.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

This adapter allows to reuse the default HTML GUI even if there is no REST API available (non-web environments).

The adapter:

1. Either connects to a JMX server ([JMX support must be enabled](http://docs.jboss.org/weld/reference/latest/en-US/html/configure.html#config-dev-mode)) or loads data from an export file
2. Starts an embedded Undertow instance
3. Exposes the default HTML client but using the data from step 1

## Build

    mvn clean package

## Download

The _shaded_ artifact is available in Maven Central:
http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22weld-probe-client-adapter%22

## Run

    java -jar weld-probe-client-adapter-1.0.0.Final-shaded.jar

### Export file

The first argument represents the path to an export file:

    java -jar weld-probe-client-adapter-1.0.0.Final-shaded.jar /home/edgar/weld-probe-export.zip

### JMX

Don't forget to allow to connect to a remote JVM process. E.g. use the following system properties: `-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false`.

### WildFly

For WildFly (standalone mode) a different jmxServiceUrl must be specified and jboss-client.jar must be also on the class path, e.g.:

    java -Dorg.jboss.weld.probe.jmxServiceUrl="service:jmx:http-remoting-jmx://127.0.0.1:9990" -cp '/opt/jboss/wildfly/bin/client/jboss-client.jar:weld-probe-client-adapter-1.0.0.Final.jar' org.jboss.weld.probe.ProbeJmx


## Configuration

| System property  | Default value | Description |
| ------------- | ------------- | ------------- |
| `org.jboss.weld.probe.jmxServiceUrl`  | service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/jmxrmi  | JMX server URL |
| `org.jboss.weld.probe.undertowHost`  | 127.0.0.1  | Undertow host - used to expose the HTML client |
| `org.jboss.weld.probe.undertowPort` | 8181  | Undertow port - used to expose the HTML client |


## Blogpost and JBoss Forge example

http://weld.cdi-spec.org/news/2015/11/10/weld-probe-jmx/
