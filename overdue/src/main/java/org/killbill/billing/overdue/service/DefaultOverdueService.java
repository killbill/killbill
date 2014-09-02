/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.overdue.service;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Named;

import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.OverdueUserApi;
import org.killbill.billing.overdue.api.DefaultOverdueUserApi;
import org.killbill.billing.overdue.config.OverdueConfig;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.listener.OverdueListener;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.xmlloader.XMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultOverdueService implements OverdueService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueService.class);

    public static final String OVERDUE_SERVICE_NAME = "overdue-service";

    private final OverdueUserApi userApi;
    private final OverdueProperties properties;
    private final OverdueNotifier asyncNotifier;
    private final OverdueNotifier checkNotifier;
    private final BusService busService;
    private final OverdueListener listener;
    private final OverdueWrapperFactory factory;

    private OverdueConfig overdueConfig;
    private boolean isConfigLoaded;

    @Inject
    public DefaultOverdueService(
            final OverdueUserApi userApi,
            final OverdueProperties properties,
            @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED) final OverdueNotifier checkNotifier,
            @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED) final OverdueNotifier asyncNotifier,
            final BusService busService,
            final OverdueListener listener,
            final OverdueWrapperFactory factory) {
        this.userApi = userApi;
        this.properties = properties;
        this.checkNotifier = checkNotifier;
        this.asyncNotifier = asyncNotifier;
        this.busService = busService;
        this.listener = listener;
        this.factory = factory;
        this.isConfigLoaded = false;
    }

    @Override
    public String getName() {
        return OVERDUE_SERVICE_NAME;
    }

    @Override
    public OverdueUserApi getUserApi() {
        return userApi;
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadConfig() throws ServiceException {
        if (!isConfigLoaded) {
            try {
                final URI u = new URI(properties.getConfigURI());
                overdueConfig = XMLLoader.getObjectFromUri(u, OverdueConfig.class);
                // File not found?
                if (overdueConfig == null) {
                    log.warn("Overdue system disabled: unable to load the overdue config from " + properties.getConfigURI());
                    overdueConfig = new OverdueConfig();
                }

                isConfigLoaded = true;
            } catch (final URISyntaxException e) {
                log.warn("Overdue system disabled: unable to load the overdue config from " + properties.getConfigURI(), e);
                overdueConfig = new OverdueConfig();
            } catch (final IllegalArgumentException e) {
                log.warn("Overdue system disabled: unable to load the overdue config from " + properties.getConfigURI(), e);
                overdueConfig = new OverdueConfig();
            } catch (final Exception e) {
                log.warn("Unable to load the overdue config from " + properties.getConfigURI(), e);
                throw new ServiceException(e);
            }

            factory.setOverdueConfig(overdueConfig);
            ((DefaultOverdueUserApi) userApi).setOverdueConfig(overdueConfig);
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        registerForBus();
        checkNotifier.initialize();
        asyncNotifier.initialize();
    }

    private void registerForBus() {
        try {
            busService.getBus().register(listener);
        } catch (final EventBusException e) {
            log.error("Problem encountered registering OverdueListener on the Event Bus", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        checkNotifier.start();
        asyncNotifier.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        try {
            busService.getBus().unregister(listener);
        } catch (final EventBusException e) {
            log.error("Problem encountered registering OverdueListener on the Event Bus", e);
        }
        checkNotifier.stop();
        asyncNotifier.stop();
    }
}
