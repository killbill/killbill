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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.server.config.KillbillServerConfig;
import com.ning.billing.server.healthchecks.KillbillHealthcheck;
import com.ning.billing.server.modules.KillbillServerModule;
import com.ning.billing.server.security.TenantFilter;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.jetty.base.modules.ServerModuleBuilder;
import com.ning.jetty.core.listeners.SetupServer;

import com.google.inject.Injector;
import com.google.inject.Module;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.management.ManagementService;

public class KillbillGuiceListener extends SetupServer {

    public static final Logger logger = LoggerFactory.getLogger(KillbillGuiceListener.class);
    public static final String KILLBILL_MULTITENANT_PROPERTY = "killbill.server.multitenant";

    private DefaultLifecycle killbillLifecycle;
    private BusService killbillBusService;
    private KillbillEventHandler killbilleventHandler;

    protected Module getModule() {
        return new KillbillServerModule();
    }

    private void registerMBeansForCache(final CacheManager cacheManager) {
        if (cacheManager != null) {
            final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ManagementService.registerMBeans(cacheManager, mBeanServer, false, true, true, true);
        }
    }

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        final boolean multitenant = Boolean.parseBoolean(System.getProperty(KILLBILL_MULTITENANT_PROPERTY, "false"));

        final ServerModuleBuilder builder = new ServerModuleBuilder()
                .addConfig(KillbillServerConfig.class)
                .addHealthCheck(KillbillHealthcheck.class)
                .addJMXExport(KillbillHealthcheck.class)
                .addJMXExport(NotificationQueueService.class)
                .addJMXExport(InternalBus.class)
                .addJMXExport(ExternalBus.class)
                .addModule(getModule())
                .addJerseyResource("com.ning.billing.jaxrs.mappers")
                .addJerseyResource("com.ning.billing.jaxrs.resources");

        if (multitenant) {
            builder.addFilter("/*", TenantFilter.class);
        }

        guiceModule = builder.build();

        super.contextInitialized(event);

        logger.info("KillbillLifecycleListener : contextInitialized");
        final Injector theInjector = injector(event);
        killbillLifecycle = theInjector.getInstance(DefaultLifecycle.class);
        killbillBusService = theInjector.getInstance(BusService.class);
        killbilleventHandler = theInjector.getInstance(KillbillEventHandler.class);

        registerMBeansForCache(theInjector.getInstance(CacheManager.class));

        /*
                ObjectMapper mapper = theInjector.getInstance(ObjectMapper.class);
                mapper.setPropertyNamingStrategy(new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy());
        */

        //
        // Fire all Startup levels up to service start
        //
        killbillLifecycle.fireStartupSequencePriorEventRegistration();
        //
        // Perform Bus registration
        //
        try {
            killbillBusService.getBus().register(killbilleventHandler);
        } catch (InternalBus.EventBusException e) {
            logger.error("Failed to register for event notifications, this is bad exiting!", e);
            System.exit(1);
        }
        // Let's start!
        killbillLifecycle.fireStartupSequencePostEventRegistration();
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        super.contextDestroyed(sce);

        logger.info("IrsKillbillListener : contextDestroyed");
        // Stop services
        // Guice error, no need to fill the screen with useless stack traces
        if (killbillLifecycle == null) {
            return;
        }

        killbillLifecycle.fireShutdownSequencePriorEventUnRegistration();

        try {
            killbillBusService.getBus().unregister(killbilleventHandler);
        } catch (InternalBus.EventBusException e) {
            logger.warn("Failed to unregister for event notifications", e);
        }

        // Complete shutdown sequence
        killbillLifecycle.fireShutdownSequencePostEventUnRegistration();
    }
}
