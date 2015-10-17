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

package org.killbill.billing.entitlement.glue;

import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultSubscriptionApi;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.svcs.DefaultEntitlementInternalApi;
import org.killbill.billing.entitlement.api.svcs.DefaultInternalBlockingApi;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.DefaultBlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.dao.ProxyBlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.glue.EntitlementModule;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.glue.SecurityModule;

import com.google.inject.TypeLiteral;

public class DefaultEntitlementModule extends KillBillModule implements EntitlementModule {

    public DefaultEntitlementModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installEntitlementPluginApi() {
        bind(new TypeLiteral<OSGIServiceRegistration<EntitlementPluginApi>>() {}).toProvider(DefaultEntitlementProviderPluginRegistryProvider.class).asEagerSingleton();
        bind(EntitlementPluginExecution.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installBlockingStateDao();
        installBlockingApi();
        installEntitlementApi();
        installEntitlementInternalApi();
        installSubscriptionApi();
        installBlockingChecker();
        bind(EntitlementService.class).to(DefaultEntitlementService.class).asEagerSingleton();
        bind(EntitlementUtils.class).asEagerSingleton();
        bind(EventsStreamBuilder.class).asEagerSingleton();
        installEntitlementPluginApi();
    }

    @Override
    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(ProxyBlockingStateDao.class).asEagerSingleton();
    }

    @Override
    public void installBlockingApi() {
        bind(BlockingInternalApi.class).to(DefaultInternalBlockingApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementApi() {
        bind(EntitlementApi.class).to(DefaultEntitlementApi.class).asEagerSingleton();
    }

    @Override
    public void installEntitlementInternalApi() {
        bind(EntitlementInternalApi.class).to(DefaultEntitlementInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionApi() {
        bind(SubscriptionApi.class).to(DefaultSubscriptionApi.class).asEagerSingleton();
    }

    public void installBlockingChecker() {
        bind(BlockingChecker.class).to(DefaultBlockingChecker.class).asEagerSingleton();
    }
}
