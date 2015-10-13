/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.probe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;

/**
 * Connects to a remote JMX server, allows to choose one of available Weld containers (with Probe JMX support enabled) and then starts an embedded Undertow,
 * exposing the default HTML client but using JMX to get the data from the selected Weld container.
 *
 * @author Martin Kouba
 * @see WELD-2015
 */
public class ProbeJmx {

    static final String SYSTEM_PROPERTY_JMX_SERVICE_HOST = "org.jboss.weld.probe.jmxServiceHost";

    static final String SYSTEM_PROPERTY_JMX_SERVICE_PORT = "org.jboss.weld.probe.jmxServicePort";

    static final String SYSTEM_PROPERTY_UT_HOST = "org.jboss.weld.probe.undertowHost";

    static final String SYSTEM_PROPERTY_UT_PORT = "org.jboss.weld.probe.undertowPort";

    static final String PROBE_JMX_APP = "probe-jmx";

    static final String PROBE_FILTER_NAME = "Weld Probe Filter";

    public static void main(String[] args) {
        new ProbeJmx().run();
    }

    void run() {

        final String jmxServiceUrl = "service:jmx:rmi:///jndi/rmi://" + System.getProperty(SYSTEM_PROPERTY_JMX_SERVICE_HOST, "127.0.0.1") + ":"
                + System.getProperty(SYSTEM_PROPERTY_JMX_SERVICE_PORT, "9999") + "/jmxrmi";
        System.out.println("Connecting to a remote JMX server: " + jmxServiceUrl);

        try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl), null)) {

            MBeanServerConnection connection = jmxc.getMBeanServerConnection();
            ObjectName queryName;
            try {
                queryName = new ObjectName(Probe.class.getPackage().getName() + ":type=JsonData,context=*");
            } catch (MalformedObjectNameException e) {
                throw new RuntimeException(e);
            }
            List<ObjectName> names = new ArrayList<>(connection.queryNames(queryName, null));

            if (names.isEmpty()) {
                System.err.println("No Weld containers with Probe JMX enabled");
                System.exit(1);
            }

            Console console = new Console();

            StringBuilder prompt = new StringBuilder();
            prompt.append("Choose a Weld container (JSON data provider):");
            prompt.append(System.lineSeparator());
            for (ListIterator<ObjectName> iterator = names.listIterator(); iterator.hasNext();) {
                prompt.append("[");
                prompt.append(iterator.nextIndex());
                prompt.append("]");
                prompt.append(" ");
                prompt.append(iterator.next());
                prompt.append(System.lineSeparator());
            }
            prompt.append(System.lineSeparator());

            int index = Integer.valueOf(console.readLine(prompt.toString()));
            if (index < names.size()) {

                ObjectName name = names.get(index);
                System.out.println("Connecting to the Weld container [" + index + "]: " + name);

                final JsonDataProvider dataProvider = JMX.newMXBeanProxy(connection, name, JsonDataProvider.class);

                String undertowHost = System.getProperty(SYSTEM_PROPERTY_UT_HOST, "127.0.0.1");
                int undertowPort = Integer.valueOf(System.getProperty(SYSTEM_PROPERTY_UT_PORT, "8181"));
                System.out.println("Starting Undertow...");

                DeploymentInfo servletBuilder = Servlets.deployment().setClassLoader(ProbeJmx.class.getClassLoader()).setContextPath("/" + PROBE_JMX_APP)
                        .setDeploymentName("probe-jmx.war")
                        .addFilter(Servlets.filter(PROBE_FILTER_NAME, SimpleProbeFilter.class, new InstanceFactory<SimpleProbeFilter>() {
                            @Override
                            public InstanceHandle<SimpleProbeFilter> createInstance() throws InstantiationException {
                                return new ImmediateInstanceHandle<SimpleProbeFilter>(new SimpleProbeFilter(dataProvider));
                            }
                        })).addFilterUrlMapping(PROBE_FILTER_NAME, "/*", DispatcherType.REQUEST);

                DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
                manager.deploy();
                PathHandler path;
                try {
                    path = Handlers.path(Handlers.redirect(PROBE_JMX_APP)).addPrefixPath(PROBE_JMX_APP, manager.start());
                } catch (ServletException e) {
                    throw new RuntimeException(e);
                }

                Undertow server = Undertow.builder().addHttpListener(undertowPort, undertowHost).setHandler(path).build();
                server.start();

                prompt = new StringBuilder();
                prompt.append("Weld Probe HTML client available at: http://");
                prompt.append(undertowHost);
                prompt.append(":");
                prompt.append(undertowPort);
                prompt.append("/");
                prompt.append(PROBE_JMX_APP);
                prompt.append("/weld-probe");
                prompt.append(System.lineSeparator());
                prompt.append("Type ENTER to stop the container...");
                prompt.append(System.lineSeparator());

                String command = console.readLine(prompt.toString());
                if (command != null) {
                    server.stop();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not connect to a remote JMX server", e);
        }
    }

    private static class Console {

        BufferedReader reader;
        PrintStream print;

        Console() {
            reader = new BufferedReader(new InputStreamReader(System.in));
            print = System.out;
        }

        String readLine(String format, Object... objects) {
            format(format, objects);
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }

        PrintStream format(String format, Object... objects) {
            return print.format(format, objects);
        }
    }

}
