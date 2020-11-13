/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.server.listeners;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletContext;

import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.config.DefaultKillbillConfigSource;
import org.killbill.billing.server.filters.Jersey1BackwardCompatibleFilter;
import org.killbill.billing.server.filters.KillbillMDCInsertingServletFilter;
import org.killbill.billing.server.filters.ProfilingContainerResponseFilter;
import org.killbill.billing.server.filters.RequestDataFilter;
import org.killbill.billing.server.filters.ResponseCorsFilter;
import org.killbill.billing.server.modules.KillbillServerModule;
import org.killbill.billing.server.notifications.PushNotificationListener;
import org.killbill.billing.server.providers.KillbillExceptionListener;
import org.killbill.billing.server.security.TenantFilter;
import org.killbill.billing.util.nodes.KillbillVersions;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.skeleton.modules.BaseServerModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.helpers.MDCInsertingServletFilter;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;
import com.google.inject.servlet.ServletModule;
import io.swagger.jaxrs.config.BeanConfig;

public class KillbillGuiceListener extends KillbillPlatformGuiceListener {

    private static final Logger logger = LoggerFactory.getLogger(KillbillGuiceListener.class);

    // See io.swagger.jaxrs.listing.ApiListingResource
    private static final String SWAGGER_PATH = "swagger.*";

    private KillbillEventHandler killbilleventHandler;

    @Override
    protected ServletModule getServletModule() {
        // Don't filter all requests through Jersey, only the JAX-RS APIs (otherwise,
        // things like static resources, favicon, etc. are 404'ed)
        final BaseServerModuleBuilder builder = new BaseServerModuleBuilder().setJaxrsUriPattern("/" + SWAGGER_PATH + "|((/" + SWAGGER_PATH + "|" + JaxRsResourceBase.PREFIX + "|" + JaxRsResourceBase.PLUGINS_PATH + ")" + "/.*)")
                                                                             .addJerseyResourcePackage("org.killbill.billing.jaxrs.mappers")
                                                                             .addJerseyResourcePackage("org.killbill.billing.jaxrs.resources")
                                                                             // Swagger integration
                                                                             .addJerseyResourcePackage("io.swagger.jaxrs.listing");

        // Jackson integration
        builder.addJerseyResourceClass(JacksonJsonProvider.class.getName());

        // Set the per-thread RequestData first
        builder.addJerseyResourceClass(RequestDataFilter.class.getName());

        // Logback default MDC
        builder.addFilter("/*", MDCInsertingServletFilter.class);

        // Kill Bill specific MDC
        builder.addJerseyResourceClass(KillbillMDCInsertingServletFilter.class.getName());

        // Jersey 1 backward compatibility
        builder.addJerseyResourceClass(Jersey1BackwardCompatibleFilter.class.getName());

        // Disable WADL
        builder.addJerseyParam("jersey.config.server.wadl.disableWadl", "true");

        if (config.isConfiguredToReturnGZIPResponses()) {
            logger.info("Enable http gzip responses");
            builder.addJerseyResourceClass(EncodingFilter.class.getName());
            builder.addJerseyResourceClass(GZipEncoder.class.getName());
        }
        builder.addJerseyResourceClass(ProfilingContainerResponseFilter.class.getName());

        // Broader, to support the "Try it out!" feature
        //builder.addFilter("/" + SWAGGER_PATH + "*", ResponseCorsFilter.class);
        builder.addFilter("/*", ResponseCorsFilter.class);

        // Add TenantFilter right after if multi-tenancy has been configured.
        if (config.isMultiTenancyEnabled()) {
            builder.addFilter("/*", TenantFilter.class);
        }

        // We use Jersey's LoggingFeature -- this adds additional logging
        builder.addJerseyResourceClass(KillbillExceptionListener.class.getName());

        return builder.build();
    }

    @Override
    protected Module getModule(final ServletContext servletContext) {
        return new KillbillServerModule(servletContext, config, configSource);
    }

    @Override
    protected KillbillConfigSource getConfigSource() throws IOException, URISyntaxException {
        final ImmutableMap<String, String> defaultProperties = ImmutableMap.<String, String>of("org.killbill.server.updateCheck.url",
                                                                                               "https://raw.github.com/killbill/killbill/master/profiles/killbill/src/main/resources/update-checker/killbill-server-update-list.properties");
        return new DefaultKillbillConfigSource(defaultProperties);
    }

    @Override
    protected void startLifecycleStage2() {
        killbilleventHandler = injector.getInstance(KillbillEventHandler.class);

        // Perform Bus registration
        try {
            killbillBusService.getBus().register(killbilleventHandler);
        } catch (final PersistentBus.EventBusException e) {
            logger.error("Failed to register for event notifications, this is bad exiting!", e);
            System.exit(1);
        }
    }

    @Override
    protected void stopLifecycleStage2() {
        super.stopLifecycleStage2();

        try {
            killbillBusService.getBus().unregister(killbilleventHandler);
        } catch (final PersistentBus.EventBusException e) {
            logger.warn("Failed to unregister for event notifications", e);
        }
    }

    @Override
    protected void startLifecycleStage3() {
        super.startLifecycleStage3();

        final BeanConfig beanConfig = new BeanConfig();
        beanConfig.setResourcePackage("org.killbill.billing.jaxrs.resources");
        beanConfig.setTitle("Kill Bill");
        beanConfig.setDescription("Kill Bill is an open-source billing and payments platform");
        beanConfig.setContact("killbilling-users@googlegroups.com");
        beanConfig.setLicense("Apache License, Version 2.0");
        beanConfig.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        beanConfig.setVersion(KillbillVersions.getKillbillVersion());
        beanConfig.setScan(true);
    }

    @Override
    protected void stopLifecycleStage3() {
        super.stopLifecycleStage3();

        final PushNotificationListener pushNotificationListener = injector.getInstance(PushNotificationListener.class);
        try {
            pushNotificationListener.shutdown();
        } catch (final IOException e) {
            logger.warn("Failed close the push notifications client", e);
        }
    }
}
