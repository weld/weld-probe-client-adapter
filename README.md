# weld-probe-jmx

This is a Weld Probe JMX client reusing the default HTML GUI.

## Build and run

> mvn clean package

> java -jar target/weld-probe-jmx-1.0.0-SNAPSHOT-shaded.jar

## Configuration

| System property  | Default value | Description |
| ------------- | ------------- | ------------- |
| org.jboss.weld.probe.jmxServiceHost  | 127.0.0.1  | JMX server host  |
| org.jboss.weld.probe.jmxServicePort  | 9999 | JMX server port  |
| org.jboss.weld.probe.undertowHost  | 127.0.0.1  | Undertow host - used to expose the HTML client |
| org.jboss.weld.probe.undertowPort | 8181  | Undertow port - used to expose the HTML client |
