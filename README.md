# weld-probe-jmx

This is a Weld Probe JMX client reusing the default HTML GUI.

## Build and run

> mvn clean package

> java -jar target/weld-probe-jmx-1.0.0-SNAPSHOT-shaded.jar

Don't forget to allow to connect to a remote JVM process. E.g. use the following system properties: `-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false`.

## Configuration

| System property  | Default value | Description |
| ------------- | ------------- | ------------- |
| org.jboss.weld.probe.jmxServiceUrl  | service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/jmxrmi  | JMX server URL |
| org.jboss.weld.probe.undertowHost  | 127.0.0.1  | Undertow host - used to expose the HTML client |
| org.jboss.weld.probe.undertowPort | 8181  | Undertow port - used to expose the HTML client |

## WildFly

For WildFly (standalone mode) a different jmxServiceUrl must be specified and jboss-client.jar must be also on the class path, e.g.:

> java -Dorg.jboss.weld.probe.jmxServiceUrl="service:jmx:http-remoting-jmx://127.0.0.1:9990" -cp '/opt/jboss/wildfly/bin/client/jboss-client.jar:target/weld-probe-jmx-1.0.0-SNAPSHOT-shaded.jar' org.jboss.weld.probe.ProbeJmx

## Blogpost and JBoss Forge example

http://weld.cdi-spec.org/news/2015/11/10/weld-probe-jmx/