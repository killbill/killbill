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

package org.killbill.billing.subscription.glue;

import org.killbill.billing.glue.SubscriptionModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.alignment.PlanAligner;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import org.killbill.billing.subscription.api.timeline.DefaultSubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.DefaultSubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseApiService;
import org.killbill.billing.subscription.catalog.DefaultSubscriptionCatalogApi;
import org.killbill.billing.subscription.catalog.SubscriptionCatalogApi;
import org.killbill.billing.subscription.config.MultiTenantSubscriptionConfig;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.subscription.engine.dao.DefaultSubscriptionDao;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.util.config.definition.SubscriptionConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.name.Names;

public class DefaultSubscriptionModule extends KillBillModule implements SubscriptionModule {

    public DefaultSubscriptionModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installConfig() {
        installConfig(new AugmentedConfigurationObjectFactory(skifeConfigSource).build(SubscriptionConfig.class));
    }

    protected void installConfig(final SubscriptionConfig staticInvoiceConfig) {
        bind(SubscriptionConfig.class).annotatedWith(Names.named(KillBillModule.STATIC_CONFIG)).toInstance(staticInvoiceConfig);
        bind(SubscriptionConfig.class).to(MultiTenantSubscriptionConfig.class).asEagerSingleton();
    }


    protected void installSubscriptionDao() {
        bind(SubscriptionDao.class).to(DefaultSubscriptionDao.class).asEagerSingleton();
    }

    protected void installSubscriptionCore() {
        bind(SubscriptionCatalogApi.class).to(DefaultSubscriptionCatalogApi.class).asEagerSingleton();
        bind(SubscriptionBaseApiService.class).to(DefaultSubscriptionBaseApiService.class).asEagerSingleton();
        bind(DefaultSubscriptionBaseService.class).asEagerSingleton();
        bind(PlanAligner.class).asEagerSingleton();
        bind(AddonUtils.class).asEagerSingleton();
        installSubscriptionService();
        installSubscriptionTimelineApi();
        installSubscriptionInternalApi();
        installSubscriptionTransferApi();
    }

    @Override
    protected void configure() {
        installConfig();
        installSubscriptionDao();
        installSubscriptionCore();
    }

    @Override
    public void installSubscriptionService() {
        bind(SubscriptionBaseService.class).to(DefaultSubscriptionBaseService.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionBaseTimelineApi.class).to(DefaultSubscriptionBaseTimelineApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionInternalApi() {
        bind(SubscriptionBaseInternalApi.class).to(DefaultSubscriptionInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionBaseTransferApi.class).to(DefaultSubscriptionBaseTransferApi.class).asEagerSingleton();
    }
}
