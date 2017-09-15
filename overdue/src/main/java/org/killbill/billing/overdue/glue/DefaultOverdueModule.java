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

package org.killbill.billing.overdue.glue;

import org.killbill.billing.glue.OverdueModule;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.DefaultOverdueApi;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.caching.EhCacheOverdueConfigCache;
import org.killbill.billing.overdue.caching.OverdueCacheInvalidationCallback;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.listener.OverdueListener;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotifier;
import org.killbill.billing.overdue.notification.OverdueAsyncBusPoster;
import org.killbill.billing.overdue.notification.OverdueCheckNotifier;
import org.killbill.billing.overdue.notification.OverdueCheckPoster;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.name.Names;

public class DefaultOverdueModule extends KillBillModule implements OverdueModule {

    public static final String OVERDUE_INVALIDATION_CALLBACK = "overdueInvalidationCallback";
    public static final String OVERDUE_NOTIFIER_CHECK_NAMED = "overdueNotifierCheck";
    public static final String OVERDUE_NOTIFIER_ASYNC_BUS_NAMED = "overdueNotifierAsyncBus";

    public DefaultOverdueModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        installOverdueUserApi();

        installOverdueConfigCache();

        // internal bindings
        installOverdueService();
        installOverdueWrapperFactory();

        final OverdueProperties config = new ConfigurationObjectFactory(skifeConfigSource).build(OverdueProperties.class);
        bind(OverdueProperties.class).toInstance(config);

        bind(OverdueListener.class).asEagerSingleton();

        bind(OverdueNotifier.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_CHECK_NAMED)).to(OverdueCheckNotifier.class).asEagerSingleton();
        bind(OverdueNotifier.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)).to(OverdueAsyncBusNotifier.class).asEagerSingleton();

        bind(OverduePoster.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_CHECK_NAMED)).to(OverdueCheckPoster.class).asEagerSingleton();
        bind(OverduePoster.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)).to(OverdueAsyncBusPoster.class).asEagerSingleton();
    }

    protected void installOverdueService() {
        bind(OverdueService.class).to(DefaultOverdueService.class).asEagerSingleton();
    }

    protected void installOverdueWrapperFactory() {
        bind(OverdueWrapperFactory.class).asEagerSingleton();
    }

    @Override
    public void installOverdueUserApi() {
        bind(OverdueApi.class).to(DefaultOverdueApi.class).asEagerSingleton();
    }

    public void installOverdueConfigCache() {
        bind(OverdueConfigCache.class).to(EhCacheOverdueConfigCache.class).asEagerSingleton();
        bind(CacheInvalidationCallback.class).annotatedWith(Names.named(OVERDUE_INVALIDATION_CALLBACK)).to(OverdueCacheInvalidationCallback.class).asEagerSingleton();
    }
}
