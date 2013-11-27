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

package com.ning.billing.entitlement.glue;

import org.skife.config.ConfigSource;

import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.EntitlementInternalApi;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.DefaultSubscriptionApi;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.SubscriptionApi;
import com.ning.billing.entitlement.api.svcs.DefaultEntitlementInternalApi;
import com.ning.billing.entitlement.api.svcs.DefaultInternalBlockingApi;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.DefaultBlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.ProxyBlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.entitlement.engine.core.EventsStreamBuilder;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.junction.BlockingInternalApi;

import com.google.inject.AbstractModule;

public class DefaultEntitlementModule extends AbstractModule implements EntitlementModule {

    public DefaultEntitlementModule(final ConfigSource configSource) {
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
