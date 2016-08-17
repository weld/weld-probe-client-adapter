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

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.weld.probe.Resource.HttpMethod;

/**
 * This servlet filter implements the Probe REST API.
 *
 * @author Martin Kouba
 */
public class SimpleProbeFilter implements Filter {

    static final String REST_URL_PATTERN_BASE = "/weld-probe";

    static final Logger LOGGER = Logger.getLogger(SimpleProbeFilter.class.getName());

    private JsonDataProvider jsonDataProvider;

    SimpleProbeFilter(JsonDataProvider jsonDataProvider) {
        this.jsonDataProvider = jsonDataProvider;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final HttpServletResponse httpResponse = (HttpServletResponse) response;
        final String[] resourcePathParts = getResourcePathParts(httpRequest.getRequestURI(), httpRequest.getServletContext().getContextPath());

        if (resourcePathParts != null) {
            // Probe resource
            HttpMethod method = HttpMethod.from(httpRequest.getMethod());
            if (method == null) {
                // Unsupported protocol
                if (httpRequest.getProtocol().endsWith("1.1")) {
                    httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                } else {
                    httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
                return;
            }
            processResourceRequest(httpRequest, httpResponse, method, resourcePathParts);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    private void processResourceRequest(HttpServletRequest req, HttpServletResponse resp, HttpMethod httpMethod, String[] resourcePathParts)
            throws IOException {
        Resource resource;
        if (resourcePathParts.length == 0) {
            resource = Resource.CLIENT_RESOURCE;
        } else {
            resource = matchResource(resourcePathParts);
            if (resource == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        ProbeLogger.LOG.resourceMatched(resource, req.getRequestURI());
        try {
            resource.handle(httpMethod, jsonDataProvider, resourcePathParts, req, resp);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Cannot handle " + httpMethod + " for " + resource, e.getCause() != null ? e.getCause() : e);
        }
    }

    private Resource matchResource(String[] resourcePathParts) {
        for (Resource resource : Resource.values()) {
            if (resource.matches(resourcePathParts)) {
                return resource;
            }
        }
        return null;
    }

    /**
     *
     * @return the array of resource path parts or <code>null</code> if the given URI does not represent a Probe resource
     */
    static String[] getResourcePathParts(String requestUri, String contextPath) {
        final String path = requestUri.substring(contextPath.length(), requestUri.length());
        if (path.startsWith(REST_URL_PATTERN_BASE)) {
            return Resource.splitPath(path.substring(REST_URL_PATTERN_BASE.length(), path.length()));
        }
        return null;
    }

}
