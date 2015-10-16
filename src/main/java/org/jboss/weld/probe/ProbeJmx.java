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

    static final String SYSTEM_PROPERTY_JMX_SERVICE_URL = "org.jboss.weld.probe.jmxServiceUrl";

    static final String SYSTEM_PROPERTY_UT_HOST = "org.jboss.weld.probe.undertowHost";

    static final String SYSTEM_PROPERTY_UT_PORT = "org.jboss.weld.probe.undertowPort";

    static final String PROBE_JMX_APP = "probe-jmx";

    static final String PROBE_FILTER_NAME = "Weld Probe Filter";

    static final String DEFAULT_JMX_SERVICE_URL = "service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/jmxrmi";

    public static void main(String[] args) {
        new ProbeJmx().start();
    }

    private Console console;

    private List<ObjectName> names;

    private Undertow undertow;

    private String undertowHost;

    private int undertowPort;

    private MBeanServerConnection connection;

    private Integer currentIndex;

    void start() {

        String jmxServiceUrl = System.getProperty(SYSTEM_PROPERTY_JMX_SERVICE_URL, DEFAULT_JMX_SERVICE_URL);
        System.out.println("Connecting to a remote JMX server: " + jmxServiceUrl);

        try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl), null)) {

            connection = jmxc.getMBeanServerConnection();
            ObjectName queryName;
            try {
                queryName = new ObjectName(Probe.class.getPackage().getName() + ":type=JsonData,context=*");
            } catch (MalformedObjectNameException e) {
                throw new RuntimeException(e);
            }
            names = new ArrayList<>(connection.queryNames(queryName, null));

            if (names.isEmpty()) {
                System.err.println("No Weld containers with Probe JMX enabled");
                System.exit(1);
            }

            undertowHost = System.getProperty(SYSTEM_PROPERTY_UT_HOST, "127.0.0.1");
            undertowPort = Integer.valueOf(System.getProperty(SYSTEM_PROPERTY_UT_PORT, "8181"));
            console = new Console();
            String command = "c";

            do {
                processCommand(command);
            } while (!isExit(command = commandPrompt()));

            stopUndertow();

        } catch (IOException e) {
            throw new RuntimeException("Could not connect to a remote JMX server", e);
        }
    }

    private void processCommand(String command) {
        if ("c".equals(command) || "connect".equals(command)) {
            String indexStr = selectionPrompt();
            Integer index;
            while ((index = parseIndexStr(indexStr)) == null || index > names.size() || index < 0) {
                indexStr = selectionPrompt();
            }
            currentIndex = index;
            reconnect(index, names.get(index));
        } else if ("h".equals(command) || "help".equals(command)) {
            System.out.println("Help - available commands: ");
            System.out.println("'e' or 'exit' to exit?");
            System.out.println("'c' or 'connect' to connect/reconnect to a Weld container");
            System.out.println("'h' or 'help' to show this help");
        } else {
            System.out.println("Connected to the Weld container [" + currentIndex + "]: " + names.get(currentIndex));
        }
    }

    private boolean isExit(String command) {
        return "e".equals(command) || "exit".equals(command);
    }

    private String commandPrompt() {
        StringBuilder prompt = new StringBuilder();
        if (currentIndex == null) {
            prompt.append("[disconnected");
        } else {
            prompt.append("[connected #");
            prompt.append(currentIndex);
        }
        prompt.append("]$ ");
        return console.readLine(prompt.toString());
    }

    private String selectionPrompt() {
        StringBuilder select = new StringBuilder();
        select.append("Select a Weld container (JSON data provider):");
        select.append(System.lineSeparator());
        for (ListIterator<ObjectName> iterator = names.listIterator(); iterator.hasNext();) {
            select.append("[");
            select.append(iterator.nextIndex());
            select.append("]");
            select.append(" ");
            select.append(iterator.next());
            select.append(System.lineSeparator());
        }
        System.out.println(select);
        return commandPrompt();
    }

    private Integer parseIndexStr(String indexStr) {
        try {
            return Integer.valueOf(indexStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void reconnect(Integer index, ObjectName mBeanName) {

        System.out.println("Connecting to the Weld container [" + index + "]: " + mBeanName);

        stopUndertow();

        final JsonDataProvider jsonDataProvider = JMX.newMXBeanProxy(connection, mBeanName, JsonDataProvider.class);

        System.out.println("Starting Undertow...");

        DeploymentInfo servletBuilder = Servlets.deployment().setClassLoader(ProbeJmx.class.getClassLoader()).setContextPath("/" + PROBE_JMX_APP)
                .setDeploymentName("probe-jmx.war")
                .addFilter(Servlets.filter(PROBE_FILTER_NAME, SimpleProbeFilter.class, new InstanceFactory<SimpleProbeFilter>() {
                    @Override
                    public InstanceHandle<SimpleProbeFilter> createInstance() throws InstantiationException {
                        return new ImmediateInstanceHandle<SimpleProbeFilter>(new SimpleProbeFilter(jsonDataProvider));
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
        undertow = Undertow.builder().addHttpListener(undertowPort, undertowHost).setHandler(path).build();
        undertow.start();

        StringBuilder info = new StringBuilder();
        info = new StringBuilder();
        info.append("Weld Probe HTML client available at: http://");
        info.append(undertowHost);
        info.append(":");
        info.append(undertowPort);
        info.append("/");
        info.append(PROBE_JMX_APP);
        info.append("/weld-probe");
        info.append(System.lineSeparator());
        System.out.println(info);
    }

    private void stopUndertow() {
        if (undertow != null) {
            System.out.println("Stopping Undertow...");
            undertow.stop();
        }
    }

    private static class Console {

        private final BufferedReader reader;

        private final PrintStream print;

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
