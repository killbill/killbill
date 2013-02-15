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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;
import javax.servlet.Servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.osgi.api.OSGIServiceRegistration;

@Singleton
public class DefaultServletRouter implements OSGIServiceRegistration<Servlet> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServletRouter.class);

    // Internal Servlet routing table: map of plugin prefixes to servlet instances.
    // A plugin prefix can be foo, foo/bar, foo/bar/baz, ... and is mounted on /plugins/<pluginPrefix>
    private final Map<String, Servlet> pluginServlets = new ConcurrentHashMap<String, Servlet>();

    @Override
    public void registerService(final String pathPrefix, final Servlet httpServlet) {
        logger.info("Registering OSGI servlet at " + pathPrefix);
        pluginServlets.put(pathPrefix, httpServlet);
    }

    @Override
    public void unregisterService(final String pathPrefix) {
        logger.info("Unregistering OSGI servlet at " + pathPrefix);
        pluginServlets.remove(pathPrefix);
    }

    @Override
    public Servlet getServiceForPluginName(final String pathPrefix) {
        return getServletForPathPrefix(pathPrefix);
    }

    @Override
    public Set<String> getAllServiceForPluginName() {
        return pluginServlets.keySet();
    }

    @Override
    public Class<Servlet> getServiceType() {
        return Servlet.class;
    }

    // TODO PIERRE Naive implementation - we should rather switch to e.g. heap tree
    public String getPluginPrefixForPath(final String pathPrefix) {
        String bestMatch = null;
        for (final String potentialMatch : pluginServlets.keySet()) {
            if (pathPrefix.startsWith(potentialMatch) && (bestMatch == null || bestMatch.length() < potentialMatch.length())) {
                bestMatch = potentialMatch;
            }
        }
        return bestMatch;
    }

    private Servlet getServletForPathPrefix(final String pathPrefix) {
        final String bestMatch = getPluginPrefixForPath(pathPrefix);
        return bestMatch == null ? null : pluginServlets.get(bestMatch);
    }
}
