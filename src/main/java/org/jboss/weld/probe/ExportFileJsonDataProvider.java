/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import static org.jboss.weld.probe.Strings.ADDITIONAL_BDA_SUFFIX;
import static org.jboss.weld.probe.Strings.APPLICATION;
import static org.jboss.weld.probe.Strings.BDA;
import static org.jboss.weld.probe.Strings.BDA_ID;
import static org.jboss.weld.probe.Strings.BEAN_CLASS;
import static org.jboss.weld.probe.Strings.BEAN_TYPE;
import static org.jboss.weld.probe.Strings.CHILDREN;
import static org.jboss.weld.probe.Strings.CONTAINER;
import static org.jboss.weld.probe.Strings.DATA;
import static org.jboss.weld.probe.Strings.DECLARING_BEAN;
import static org.jboss.weld.probe.Strings.DECLARING_CLASS;
import static org.jboss.weld.probe.Strings.DESCRIPTION;
import static org.jboss.weld.probe.Strings.EVENT_INFO;
import static org.jboss.weld.probe.Strings.FIRED;
import static org.jboss.weld.probe.Strings.ID;
import static org.jboss.weld.probe.Strings.INTERCEPTED_BEAN;
import static org.jboss.weld.probe.Strings.INVOCATIONS;
import static org.jboss.weld.probe.Strings.IS_ALTERNATIVE;
import static org.jboss.weld.probe.Strings.KIND;
import static org.jboss.weld.probe.Strings.LAST_PAGE;
import static org.jboss.weld.probe.Strings.METHOD_NAME;
import static org.jboss.weld.probe.Strings.OBSERVED_TYPE;
import static org.jboss.weld.probe.Strings.PAGE;
import static org.jboss.weld.probe.Strings.QUALIFIER;
import static org.jboss.weld.probe.Strings.QUALIFIERS;
import static org.jboss.weld.probe.Strings.RECEPTION;
import static org.jboss.weld.probe.Strings.SCOPE;
import static org.jboss.weld.probe.Strings.SEARCH;
import static org.jboss.weld.probe.Strings.STEREOTYPES;
import static org.jboss.weld.probe.Strings.TOTAL;
import static org.jboss.weld.probe.Strings.TX_PHASE;
import static org.jboss.weld.probe.Strings.TYPE;
import static org.jboss.weld.probe.Strings.TYPES;
import static org.jboss.weld.probe.Strings.UNUSED;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.enterprise.event.Reception;
import javax.enterprise.event.TransactionPhase;

import org.jboss.weld.probe.Components.BeanKind;
import org.jboss.weld.probe.Queries.Filters;
import org.jboss.weld.probe.Queries.Page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Loads JSON data from an export file.
 *
 * @author Martin Kouba
 */
class ExportFileJsonDataProvider implements JsonDataProvider {

    private static final int DEFAULT_BUFFER_SIZE = 2048;

    private static final Logger LOGGER = Logger.getLogger(ExportFileJsonDataProvider.class.getName());

    private final String deploymentJson;

    private final JsonArray contexts;

    private final Map<String, JsonElement> contextsMap = new HashMap<>();

    private final Map<String, String> bdasMap = new HashMap<>();

    private final JsonArray beans;

    private final JsonArray observers;

    private final JsonArray events;

    private final JsonArray invocations;

    ExportFileJsonDataProvider(File exportFile) {
        try (ZipFile zip = new ZipFile(exportFile)) {
            // DEPLOYMENT
            deploymentJson = readToString(zip, "deployment.json");
            JsonElement deploymentElement = readToJson(zip, "deployment.json");
            if (deploymentElement != null) {
                deploymentElement.getAsJsonObject().get("bdas").getAsJsonArray()
                        .forEach(bda -> bdasMap.put(bda.getAsJsonObject().get(ID).getAsString(), bda.getAsJsonObject().get(BDA_ID).getAsString()));
            }
            // CONTEXTS
            JsonElement contextsElement = readToJson(zip, "contexts.json");
            if (contextsElement != null) {
                contexts = contextsElement.getAsJsonArray();
            } else {
                contexts = new JsonArray();
            }
            for (JsonElement ctx : contexts) {
                JsonElement ctxDataElement = readToJson(zip, "context-" + ctx.getAsJsonObject().get("id").getAsString() + ".json");
                if (ctxDataElement != null) {
                    contextsMap.put(ctx.getAsJsonObject().get("id").getAsString(), ctxDataElement);
                }
            }
            // BEANS
            this.beans = readDataToJson(zip, "beans.json");
            // OBSERVERS
            this.observers = readDataToJson(zip, "observers.json");
            // EVENTS
            this.events = readDataToJson(zip, "fired-events.json");
            // INVOCATIONS
            this.invocations = readDataToJson(zip, "invocation-trees.json");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load export file: " + exportFile, e);
        }
    }

    @Override
    public String receiveDeployment() {
        return deploymentJson;
    }

    @Override
    public String receiveBeans(int pageIndex, int pageSize, String filters, String representation) {
        // Representation is ignored ATM
        Page<JsonObject> page = Queries.find(
                StreamSupport.stream(beans.spliterator(), false).map(element -> element.getAsJsonObject()).collect(Collectors.toList()), pageIndex, pageSize,
                Queries.initFilters(filters, new ExportBeanFilters(bdasMap)));
        JsonArray data = new JsonArray();
        page.getData().forEach(bean -> data.add(bean));
        return encodePage(page, data);
    }

    @Override
    public String receiveBean(String id, boolean transientDependencies, boolean transientDependents) {
        for (JsonElement bean : beans) {
            JsonElement idElement = bean.getAsJsonObject().get(ID);
            if (idElement != null && idElement.getAsString().equals(id)) {
                if (!transientDependents) {
                    JsonElement dependents = bean.getAsJsonObject().get(Strings.DEPENDENTS);
                    if (dependents != null) {
                        dependents.getAsJsonArray().forEach(dependent -> dependent.getAsJsonObject().remove(Strings.DEPENDENTS));
                    }
                }
                if (!transientDependencies) {
                    JsonElement dependencies = bean.getAsJsonObject().get(Strings.DEPENDENCIES);
                    if (dependencies != null) {
                        dependencies.getAsJsonArray().forEach(dependency -> dependency.getAsJsonObject().remove(Strings.DEPENDENCIES));
                    }
                }
                return bean.toString();
            }
        }
        throw new IllegalStateException("No bean found for: " + id);
    }

    @Override
    public String receiveBeanInstance(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String receiveObservers(int pageIndex, int pageSize, String filters, String representation) {
        // Representation is ignored ATM
        Page<JsonObject> page = Queries.find(
                StreamSupport.stream(observers.spliterator(), false).map(element -> element.getAsJsonObject()).collect(Collectors.toList()), pageIndex,
                pageSize, Queries.initFilters(filters, new ExportObserversFilters(bdasMap)));
        JsonArray data = new JsonArray();
        page.getData().forEach(bean -> data.add(bean));
        return encodePage(page, data);
    }

    @Override
    public String receiveObserver(String id) {
        for (JsonElement observer : observers) {
            JsonElement idElement = observer.getAsJsonObject().get(ID);
            if (idElement != null && idElement.getAsString().equals(id)) {
                return observer.toString();
            }
        }
        throw new IllegalStateException("No observer found for: " + id);
    }

    @Override
    public String receiveContexts() {
        return contexts.toString();
    }

    @Override
    public String receiveContext(String id) {
        return contextsMap.containsKey(id) ? contextsMap.get(id).toString() : new JsonObject().toString();
    }

    @Override
    public String receiveInvocations(int pageIndex, int pageSize, String filters, String representation) {
        // Representation is ignored ATM
        Page<JsonObject> page = Queries.find(
                StreamSupport.stream(invocations.spliterator(), false).map(element -> element.getAsJsonObject()).collect(Collectors.toList()), pageIndex,
                pageSize, Queries.initFilters(filters, new ExportInvocationsFilters()));
        JsonArray data = new JsonArray();
        page.getData().forEach(bean -> data.add(bean));
        return encodePage(page, data);
    }

    @Override
    public String clearInvocations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String receiveInvocation(String id) {
        for (JsonElement invocation : invocations) {
            JsonElement idElement = invocation.getAsJsonObject().get(ID);
            if (idElement != null && idElement.getAsString().equals(id)) {
                return invocation.toString();
            }
        }
        throw new IllegalStateException("No invocation found for: " + id);
    }

    @Override
    public String receiveEvents(int pageIndex, int pageSize, String filters) {
        Page<JsonObject> page = Queries.find(
                StreamSupport.stream(events.spliterator(), false).map(element -> element.getAsJsonObject()).collect(Collectors.toList()), pageIndex, pageSize,
                Queries.initFilters(filters, new ExportEventsFilters()));
        JsonArray data = new JsonArray();
        page.getData().forEach(bean -> data.add(bean));
        return encodePage(page, data);
    }

    @Override
    public String clearEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String receiveMonitoringStats() {
        return Json.objectBuilder().add(FIRED, events.size()).add(INVOCATIONS, invocations.size()).build();
    }

    @Override
    public String receiveAvailableBeans(int pageIndex, int pageSize, String filters, String representation) {
        throw new UnsupportedOperationException();
    }

    private String encodePage(Page<JsonObject> page, JsonArray data) {
        JsonObject pageData = new JsonObject();
        pageData.addProperty(PAGE, page.getIdx());
        pageData.addProperty(LAST_PAGE, page.getLastIdx());
        pageData.addProperty(TOTAL, page.getTotal());
        pageData.add(DATA, data);
        return pageData.toString();
    }

    private String readToString(ZipFile zip, String zipEntryName) throws IOException {
        ZipEntry entry = zip.getEntry(zipEntryName);
        if (entry != null) {
            return readToString(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8), DEFAULT_BUFFER_SIZE);
        }
        jsonDataNotAvailable(zipEntryName);
        return null;
    }

    private JsonElement readToJson(ZipFile zip, String zipEntryName) throws IOException {
        ZipEntry entry = zip.getEntry(zipEntryName);
        if (entry != null) {
            return new JsonParser().parse(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
        }
        jsonDataNotAvailable(zipEntryName);
        return null;
    }

    private JsonArray readDataToJson(ZipFile zip, String zipEntryName) throws IOException {
        JsonElement element = readToJson(zip, zipEntryName);
        return element != null ? element.getAsJsonObject().get("data").getAsJsonArray() : new JsonArray();
    }

    private void jsonDataNotAvailable(String zipEntryName) {
        LOGGER.warning(zipEntryName + " data not available");
    }

    private static String readToString(Reader input, int bufferSize) throws IOException {
        return readToString(input, bufferSize, true);
    }

    private static String readToString(Reader input, int bufferSize, boolean close) throws IOException {
        StringBuilder builder = new StringBuilder();
        try {
            copy(input, builder, bufferSize);
        } finally {
            if (close) {
                input.close();
            }
        }
        return builder.toString();
    }

    private static void copy(Readable in, Appendable out, int bufferSize) throws IOException {
        CharBuffer buffer = CharBuffer.allocate(bufferSize);
        while (in.read(buffer) != -1) {
            buffer.flip();
            out.append(buffer);
            buffer.clear();
        }
    }

    static abstract class ExportFilters extends Filters<JsonObject> {

        private final Map<String, String> bdasMap;

        public ExportFilters(Map<String, String> bdasMap) {
            super(null);
            this.bdasMap = bdasMap;
        }

        protected boolean testArrayContains(String filter, JsonArray values) {
            if (filter == null) {
                return true;
            }
            if (values != null) {
                for (JsonElement value : values) {
                    if (testContainsIgnoreCase(filter, value.getAsString())) {
                        return true;
                    }
                }
            }
            return false;
        }

        protected boolean testBda(String bda, JsonObject value) {
            if (bda == null) {
                return true;
            }
            if (value == null || !value.has(BDA_ID)) {
                return false;
            }
            if (FILTER_ADDITIONAL_BDAS_MARKER.equals(bda)) {
                return !bdasMap.get(value.get(BDA_ID).getAsString()).endsWith(ADDITIONAL_BDA_SUFFIX);
            } else {
                return value.get(BDA_ID).getAsString().equals(bda);
            }
        }

    }

    static class ExportEventsFilters extends ExportFilters {

        private Boolean container;

        private String eventInfo;

        private String type;

        private String qualifiers;

        ExportEventsFilters() {
            super(null);
        }

        @Override
        boolean test(JsonObject event) {
            return testContainsIgnoreCase(eventInfo, event.has(EVENT_INFO) ? event.get(EVENT_INFO).getAsString() : "")
                    && testContainsIgnoreCase(type, event.has(TYPE) ? event.get(TYPE).getAsString() : "")
                    && testArrayContains(qualifiers, event.has(QUALIFIERS) ? event.get(QUALIFIERS).getAsJsonArray() : null)
                    && testEquals(container, event.has(KIND) ? event.get(KIND).getAsString().equalsIgnoreCase(CONTAINER) : null);
        }

        @Override
        void processFilter(String name, String value) {
            if (Strings.EVENT_INFO.equals(name)) {
                this.eventInfo = value;
            } else if (Strings.TYPE.equals(name)) {
                this.type = value;
            } else if (Strings.QUALIFIERS.equals(name)) {
                this.qualifiers = value;
            } else if (Strings.KIND.equals(name)) {
                if (CONTAINER.equalsIgnoreCase(value)) {
                    container = true;
                } else if (APPLICATION.equalsIgnoreCase(value)) {
                    container = false;
                }
            }
        }

        @Override
        public String toString() {
            return String.format("ExportEventsFilters [container=%s, eventInfo=%s, type=%s, qualifiers=%s]", container, eventInfo, type, qualifiers);
        }

        @Override
        boolean isEmpty() {
            return container == null && eventInfo == null && type == null && qualifiers == null;
        }

    }

    static class ExportInvocationsFilters extends ExportFilters {

        private String beanClass;

        private String methodName;

        private String search;

        private String description;

        ExportInvocationsFilters() {
            super(null);
        }

        @Override
        boolean test(JsonObject invocation) {
            String beanClassValue = "";
            if (beanClass != null) {
                if (invocation.has(DECLARING_CLASS)) {
                    beanClassValue = invocation.get(DECLARING_CLASS).getAsString();
                } else if (invocation.has(INTERCEPTED_BEAN)) {
                    beanClassValue = invocation.get(INTERCEPTED_BEAN).getAsJsonObject().get(BEAN_CLASS).getAsString();
                }
            }
            return testSearch(search, invocation) && testContainsIgnoreCase(beanClass, beanClassValue)
                    && testContainsIgnoreCase(methodName, invocation.has(METHOD_NAME) ? invocation.get(METHOD_NAME).getAsString() : "")
                    && testContainsIgnoreCase(description, invocation.has(DESCRIPTION) ? invocation.get(DESCRIPTION).getAsString() : "");
        }

        @Override
        void processFilter(String name, String value) {
            if (BEAN_CLASS.equals(name)) {
                beanClass = value;
            } else if (METHOD_NAME.equals(name)) {
                methodName = value;
            } else if (SEARCH.equals(name)) {
                search = value;
            } else if (DESCRIPTION.equals(name)) {
                description = value;
            }
        }

        boolean testSearch(String search, JsonObject invocation) {
            if (search == null) {
                return true;
            }
            if (containsIgnoreCase(search, invocation.has(BEAN_CLASS) ? invocation.get(BEAN_CLASS).getAsString() : null)
                    || containsIgnoreCase(search, invocation.has(METHOD_NAME) ? invocation.get(METHOD_NAME).getAsString() : null)) {
                return true;
            }
            if (invocation.has(CHILDREN)) {
                for (JsonElement child : invocation.get(CHILDREN).getAsJsonArray()) {
                    if (testSearch(search, child.getAsJsonObject())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("ExportInvocationsFilters [beanClass=%s, methodName=%s, search=%s, description=%s]", beanClass, methodName, search,
                    description);
        }

        @Override
        boolean isEmpty() {
            return beanClass == null && methodName == null && search == null && description == null;
        }

    }

    static class ExportObserversFilters extends ExportFilters {

        private String beanClass;

        private String observedType;

        private String qualifier;

        private Reception reception;

        private TransactionPhase txPhase;

        private BeanKind declaringBeanKind;

        private String bda;

        ExportObserversFilters(Map<String, String> bdasMap) {
            super(bdasMap);
        }

        @Override
        boolean test(JsonObject observer) {
            return testBda(bda, observer.has(DECLARING_BEAN) ? observer.get(DECLARING_BEAN).getAsJsonObject() : null)
                    && testContainsIgnoreCase(beanClass, observer.has(BEAN_CLASS) ? observer.get(BEAN_CLASS).getAsString() : "")
                    && testArrayContains(qualifier, observer.has(QUALIFIERS) ? observer.get(QUALIFIERS).getAsJsonArray() : null)
                    && testEquals(declaringBeanKind,
                            observer.has(DECLARING_BEAN) ? BeanKind.from(observer.get(DECLARING_BEAN).getAsJsonObject().get(KIND).getAsString()) : null)
                    && testEquals(reception, getReception(observer)) && testEquals(txPhase, getTransactionPhase(observer))
                    && testContainsIgnoreCase(observedType, observer.has(OBSERVED_TYPE) ? observer.get(OBSERVED_TYPE).getAsString() : "");
        }

        @Override
        void processFilter(String name, String value) {
            if (KIND.equals(name)) {
                declaringBeanKind = BeanKind.from(value);
            } else if (BEAN_CLASS.equals(name)) {
                beanClass = value;
            } else if (OBSERVED_TYPE.equals(name)) {
                observedType = value;
            } else if (QUALIFIER.equals(name)) {
                qualifier = value;
            } else if (RECEPTION.equals(name)) {
                for (Reception recept : Reception.values()) {
                    if (recept.toString().equals(value)) {
                        reception = recept;
                    }
                }
            } else if (TX_PHASE.equals(name)) {
                for (TransactionPhase phase : TransactionPhase.values()) {
                    if (phase.toString().equals(value)) {
                        txPhase = phase;
                    }
                }
            } else if (BDA.equals(name)) {
                bda = value;
            }
        }

        private Reception getReception(JsonObject observer) {
            if (observer.has(RECEPTION)) {
                for (Reception reception : Reception.values()) {
                    if (reception.toString().equals(observer.get(RECEPTION).getAsString())) {
                        return reception;
                    }
                }
            }
            return null;
        }

        private TransactionPhase getTransactionPhase(JsonObject observer) {
            if (observer.has(TX_PHASE)) {
                for (TransactionPhase phase : TransactionPhase.values()) {
                    if (phase.toString().equals(observer.get(TX_PHASE).getAsString())) {
                        return phase;
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return String.format("ExportObserversFilters [beanClass=%s, observedType=%s, qualifier=%s, reception=%s, txPhase=%s, declaringBeanKind=%s, bda=%s]",
                    beanClass, observedType, qualifier, reception, txPhase, declaringBeanKind, bda);
        }

        @Override
        boolean isEmpty() {
            return beanClass == null && observedType == null && qualifier == null && reception == null && bda == null && txPhase == null
                    && declaringBeanKind == null;
        }

    }

    static class ExportBeanFilters extends ExportFilters {

        private BeanKind kind;

        private String beanClass;

        private String beanType;

        private String qualifier;

        private String scope;

        private String bda;

        private Boolean isAlternative;

        private String stereotypes;

        private Boolean unused;

        ExportBeanFilters(Map<String, String> bdasMap) {
            super(bdasMap);
        }

        @Override
        boolean test(JsonObject bean) {
            return testEquals(kind, bean.has(KIND) ? BeanKind.from(bean.get(KIND).getAsString()) : null)
                    && testEquals(unused, bean.has(UNUSED) ? bean.get(UNUSED).getAsBoolean() : null)
                    && testEquals(isAlternative, bean.has(IS_ALTERNATIVE) ? bean.get(IS_ALTERNATIVE).getAsBoolean() : null) && testBda(bda, bean)
                    && testContainsIgnoreCase(beanClass, bean.has(BEAN_CLASS) ? bean.get(BEAN_CLASS).getAsString() : "")
                    && testContainsIgnoreCase(scope, bean.has(SCOPE) ? bean.get(SCOPE).getAsString() : "")
                    && testArrayContains(beanType, bean.has(TYPES) ? bean.get(TYPES).getAsJsonArray() : null)
                    && testArrayContains(qualifier, bean.has(QUALIFIERS) ? bean.get(QUALIFIERS).getAsJsonArray() : null)
                    && testArrayContains(stereotypes, bean.has(STEREOTYPES) ? bean.get(STEREOTYPES).getAsJsonArray() : null);
        }

        @Override
        void processFilter(String name, String value) {
            if (KIND.equals(name)) {
                kind = BeanKind.from(value);
            } else if (BEAN_CLASS.equals(name)) {
                beanClass = value;
            } else if (BEAN_TYPE.equals(name)) {
                beanType = value;
            } else if (QUALIFIER.equals(name)) {
                qualifier = value;
            } else if (SCOPE.equals(name)) {
                scope = value;
            } else if (BDA.equals(name)) {
                bda = value;
            } else if (IS_ALTERNATIVE.equals(name)) {
                isAlternative = Boolean.valueOf(value);
            } else if (STEREOTYPES.equals(name)) {
                stereotypes = value;
            } else if (UNUSED.equals(name)) {
                unused = Boolean.valueOf(value);
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "ExportBeanFilters [kind=%s, beanClass=%s, beanType=%s, qualifier=%s, scope=%s, bda=%s, isAlternative=%s, stereotypes=%s, unused=%s]", kind,
                    beanClass, beanType, qualifier, scope, bda, isAlternative, stereotypes, unused);
        }

        @Override
        boolean isEmpty() {
            return kind == null && beanClass == null && beanType == null && qualifier == null && scope == null && bda == null && isAlternative == null
                    && stereotypes == null && !unused;
        }

    }

}
