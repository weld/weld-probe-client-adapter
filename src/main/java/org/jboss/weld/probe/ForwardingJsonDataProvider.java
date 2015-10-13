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

/**
 *
 * @author Martin Kouba
 */
public class ForwardingJsonDataProvider implements JsonDataProvider {

    private final JsonDataProvider delegate;

    /**
     *
     * @param delegate
     */
    private ForwardingJsonDataProvider(JsonDataProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public String receiveDeployment() {
        return delegate.receiveDeployment();
    }

    @Override
    public String receiveBeans(int pageIndex, int pageSize, String filters, String representation) {
        return delegate.receiveBeans(pageIndex, pageSize, filters, representation);
    }

    @Override
    public String receiveBean(String id, boolean transientDependencies, boolean transientDependents) {
        return delegate.receiveBean(id, transientDependencies, transientDependents);
    }

    @Override
    public String receiveBeanInstance(String id) {
        return delegate.receiveBeanInstance(id);
    }

    @Override
    public String receiveObservers(int pageIndex, int pageSize, String filters) {
        return delegate.receiveObservers(pageIndex, pageSize, filters);
    }

    @Override
    public String receiveObserver(String id) {
        return delegate.receiveObserver(id);
    }

    @Override
    public String receiveContexts() {
        return delegate.receiveContexts();
    }

    @Override
    public String receiveContext(String id) {
        return delegate.receiveContext(id);
    }

    @Override
    public String receiveInvocations(int pageIndex, int pageSize, String filters) {
        return delegate.receiveInvocations(pageIndex, pageSize, filters);
    }

    @Override
    public String clearInvocations() {
        return delegate.clearInvocations();
    }

    @Override
    public String receiveInvocation(String id) {
        return delegate.receiveInvocation(id);
    }

    @Override
    public String receiveEvents(int pageIndex, int pageSize, String filters) {
        return delegate.receiveEvents(pageIndex, pageSize, filters);
    }

    @Override
    public String clearEvents() {
        return delegate.clearEvents();
    }

}
