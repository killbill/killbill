/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.osgi.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.servlet.Servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.osgi.api.OSGIServiceDescriptor;
import com.ning.billing.osgi.api.OSGIServiceRegistration;

@Singleton
public class DefaultServletRouter implements OSGIServiceRegistration<Servlet> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServletRouter.class);

    // Internal Servlet routing table: map of plugin prefixes to servlet instances.
    // A plugin prefix can be /foo, /foo/bar, /foo/bar/baz, ... and is mounted on /plugins/<pluginPrefix>
    private final Map<String, Servlet> pluginPathServlets = new HashMap<String, Servlet>();
    private final Map<String, OSGIServiceDescriptor> pluginRegistrations = new HashMap<String, OSGIServiceDescriptor>();

    @Override
    public void registerService(final OSGIServiceDescriptor desc, final Servlet httpServlet) {
        // Enforce each route to start with /
        final String pathPrefix = getPathPrefixFromDescriptor(desc);
        if (pathPrefix == null) {
            logger.warn("Skipping registration of OSGI servlet for service {} (service info is not specified)", desc.getRegistrationName());
            return;
        }

        logger.info("Registering OSGI servlet at " + pathPrefix);
        synchronized (this) {
            registerServletInternal(pathPrefix, httpServlet);
            registerServiceInternal(desc);
        }
    }

    public void registerServiceFromPath(final String path, final Servlet httpServlet) {
        final String pathPrefix = sanitizePathPrefix(path);
        registerServletInternal(pathPrefix, httpServlet);
    }

    private void registerServletInternal(final String pathPrefix, final Servlet httpServlet) {
        pluginPathServlets.put(pathPrefix, httpServlet);
    }

    private void registerServiceInternal(final OSGIServiceDescriptor desc) {
        pluginRegistrations.put(desc.getRegistrationName(), desc);
    }

    @Override
    public void unregisterService(final String serviceName) {
        synchronized (this) {
            final OSGIServiceDescriptor desc = pluginRegistrations.get(serviceName);
            if (desc != null) {
                final String pathPrefix = getPathPrefixFromDescriptor(desc);
                if (pathPrefix == null) {
                    logger.warn("Skipping unregistration of OSGI servlet for service {} (service info is not specified)", desc.getRegistrationName());
                    return;
                }

                logger.info("Unregistering OSGI servlet " + desc.getRegistrationName() + " at path " + pathPrefix);
                synchronized (this) {
                    unRegisterServletInternal(pathPrefix);
                    unRegisterServiceInternal(desc);
                }
            }
        }
    }

    public void unregisterServiceFromPath(final String path) {
        final String pathPrefix = sanitizePathPrefix(path);
        unRegisterServletInternal(pathPrefix);
    }

    private Servlet unRegisterServletInternal(final String pathPrefix) {
        return pluginPathServlets.remove(pathPrefix);
    }

    private OSGIServiceDescriptor unRegisterServiceInternal(final OSGIServiceDescriptor desc) {
        return pluginRegistrations.remove(desc.getRegistrationName());
    }

    @Override
    public Servlet getServiceForName(final String serviceName) {
        final OSGIServiceDescriptor desc = pluginRegistrations.get(serviceName);
        if (desc == null) {
            return null;
        }
        final String registeredPath = getPathPrefixFromDescriptor(desc);
        return pluginPathServlets.get(registeredPath);
    }

    private String getPathPrefixFromDescriptor(final OSGIServiceDescriptor desc) {
        return sanitizePathPrefix(desc.getRegistrationName());
    }

    public Servlet getServiceForPath(final String path) {
        return getServletForPathPrefix(path);
    }

    @Override
    public Set<String> getAllServices() {
        return pluginPathServlets.keySet();
    }

    @Override
    public Class<Servlet> getServiceType() {
        return Servlet.class;
    }

    // TODO PIERRE Naive implementation - we should rather switch to e.g. heap tree
    public String getPluginPrefixForPath(final String pathPrefix) {
        String bestMatch = null;
        for (final String potentialMatch : pluginPathServlets.keySet()) {
            if (pathPrefix.startsWith(potentialMatch) && (bestMatch == null || bestMatch.length() < potentialMatch.length())) {
                bestMatch = potentialMatch;
            }
        }
        return bestMatch;
    }

    private Servlet getServletForPathPrefix(final String pathPrefix) {
        final String bestMatch = getPluginPrefixForPath(pathPrefix);
        return bestMatch == null ? null : pluginPathServlets.get(bestMatch);
    }

    private static String sanitizePathPrefix(final String inputPath) {
        if (inputPath == null) {
            return null;
        }

        final String pathPrefix;
        if (inputPath.charAt(0) != '/') {
            pathPrefix = "/" + inputPath;
        } else {
            pathPrefix = inputPath;
        }
        return pathPrefix;
    }
}
