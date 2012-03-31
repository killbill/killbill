/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.server.listeners;

import static com.sun.jersey.api.core.PackagesResourceConfig.PROPERTY_PACKAGES;
import static com.sun.jersey.api.core.ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS;
import static com.sun.jersey.api.core.ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;

import com.ning.billing.server.healthchecks.KillbillHealthcheck;
import com.ning.billing.server.modules.KillbillServerModule;
import com.ning.jetty.base.modules.ServerModuleBuilder;
import com.sun.jersey.api.container.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.container.filter.LoggingFilter;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;


public class KillbillGuiceModuleFactory implements GuiceModuleFactory {


    private static final List<String> FILTERS = ImmutableList.of(GZIPContentEncodingFilter.class.getName(),
            LoggingFilter.class.getName());
    private static final List<String> RESOURCES = ImmutableList.of("com.ning.billing.jaxrs.resources");

    private static final Map<String, String> JERSEY_PARAMS = ImmutableMap.of(
            PROPERTY_CONTAINER_REQUEST_FILTERS, StringUtils.join(FILTERS, ";"),
            // Though it would seem to make sense that filters should be applied to responses in reverse order, in fact the
            // response filters appear to wrap each other up before executing, with the result being that execution order
            // is the reverse of the declared order.
            PROPERTY_CONTAINER_RESPONSE_FILTERS, StringUtils.join(FILTERS, ";"),
            PROPERTY_PACKAGES, StringUtils.join(RESOURCES, ";")
    );

    private KillbillServerModule instantiateServiceModule() {
        try {
            /*
            ParameterizedType parameterizedType = (ParameterizedType)getClass().getGenericSuperclass();
            @SuppressWarnings("unchecked")
            Class<KillbillServerModule> clazz = (Class<KillbillServerModule>)parameterizedType.getActualTypeArguments()[0];
            */
            return KillbillServerModule.class.newInstance();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Module createModule() {
        final ServerModuleBuilder builder = new ServerModuleBuilder();

        builder.addHealthCheck(KillbillHealthcheck.class)
        .addJMXExport(KillbillHealthcheck.class)
        .addModule(instantiateServiceModule())
        .enableLog4J()
        .trackRequests()
        .addFilter("/1.0/*", GuiceContainer.class, JERSEY_PARAMS);
        return builder.build();
    }

}
