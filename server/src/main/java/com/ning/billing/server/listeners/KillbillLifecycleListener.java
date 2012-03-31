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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Injector;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.Bus.EventBusException;

public class KillbillLifecycleListener implements ServletContextListener {


    public static final Logger logger = LoggerFactory.getLogger(KillbillLifecycleListener.class);

    private DefaultLifecycle killbillLifecycle;
    private BusService killbillBusService;

    private KillbillEventHandler killbilleventHandler;


    @Override
    public void contextInitialized(ServletContextEvent sce) {

        logger.info("KillbillLifecycleListener : contextInitialized");
        Injector injector = KillbillGuiceListener.getInjectorInstance();
        killbillLifecycle = injector.getInstance(DefaultLifecycle.class);
        killbillBusService = injector.getInstance(BusService.class);

        killbilleventHandler = new KillbillEventHandler();

        //
        // Fire all Startup levels up to service start
        //
        killbillLifecycle.fireStartupSequencePriorEventRegistration();
        //
        // Perform Bus registration
        //
        try {
            killbillBusService.getBus().register(killbilleventHandler);
        } catch (EventBusException e) {
           logger.error("Failed to register for event notifications, this is bad exiting!", e);
           System.exit(1);
        }
        // Let's start!
        killbillLifecycle.fireStartupSequencePostEventRegistration();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("IrsKillbillListener : contextDestroyed");
        // Stop services
        // Guice error, no need to fill the screen with useless stack traces
        if (killbillLifecycle == null) {
            return;
        }

        killbillLifecycle.fireShutdownSequencePriorEventUnRegistration();

        try {
            killbillBusService.getBus().unregister(killbilleventHandler);
        } catch (EventBusException e) {
            logger.warn("Failed to unregister for event notifications", e);
        }

        // Complete shutdown sequence
        killbillLifecycle.fireShutdownSequencePostEventUnRegistration();
    }


    //
    // At this point we have one generic handler in IRS that could dispatch notifications to the various pieces
    // interested but we could the various pieces register their own handler directly
    //     //
    public static class KillbillEventHandler {

        /*
         * IRS event handler for killbill entitlement events
         */
        @Subscribe
        public void handleEntitlementevents(SubscriptionTransition event) {
            logger.info("Killbill entitlement event {}", event.toString());
        }
    }
}
