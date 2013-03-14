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

package com.ning.billing.analytics.setup;

import org.skife.config.ConfigSource;

import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.analytics.BusinessAccountDao;
import com.ning.billing.analytics.BusinessSubscriptionTransitionDao;
import com.ning.billing.analytics.BusinessTagDao;
import com.ning.billing.analytics.api.AnalyticsService;
import com.ning.billing.analytics.api.DefaultAnalyticsService;
import com.ning.billing.analytics.api.sanity.AnalyticsSanityApi;
import com.ning.billing.analytics.api.sanity.DefaultAnalyticsSanityApi;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.analytics.api.user.DefaultAnalyticsUserApi;
import com.ning.billing.analytics.dao.AnalyticsDao;
import com.ning.billing.analytics.dao.AnalyticsSanityDao;
import com.ning.billing.analytics.dao.BusinessAccountFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessOverdueStatusSqlDao;
import com.ning.billing.analytics.dao.BusinessSqlProvider;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.analytics.dao.DefaultAnalyticsDao;
import com.ning.billing.analytics.dao.DefaultAnalyticsSanityDao;

import com.google.inject.AbstractModule;

public class AnalyticsModule extends AbstractModule {

    protected final ConfigSource configSource;

    public AnalyticsModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        installAnalyticsUserApi();
        installAnalyticsSanityApi();

        installAnalyticsDao();
        installAnalyticsSqlDao();

        bind(AnalyticsListener.class).asEagerSingleton();
        bind(AnalyticsService.class).to(DefaultAnalyticsService.class).asEagerSingleton();
    }

    protected void installAnalyticsUserApi() {
        bind(DefaultAnalyticsUserApi.class).asEagerSingleton();
        bind(AnalyticsUserApi.class).to(DefaultAnalyticsUserApi.class).asEagerSingleton();
    }

    protected void installAnalyticsSanityApi() {
        bind(AnalyticsSanityApi.class).to(DefaultAnalyticsSanityApi.class).asEagerSingleton();
    }

    protected void installAnalyticsDao() {
        bind(AnalyticsDao.class).to(DefaultAnalyticsDao.class).asEagerSingleton();
        bind(AnalyticsSanityDao.class).to(DefaultAnalyticsSanityDao.class).asEagerSingleton();
        bind(BusinessSubscriptionTransitionDao.class).asEagerSingleton();
        bind(BusinessAccountDao.class).asEagerSingleton();
        bind(BusinessTagDao.class).asEagerSingleton();
    }

    protected void installAnalyticsSqlDao() {
        bind(BusinessAccountSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountSqlDao>(BusinessAccountSqlDao.class));
        bind(BusinessAccountTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountTagSqlDao>(BusinessAccountTagSqlDao.class));
        bind(BusinessAccountFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountFieldSqlDao>(BusinessAccountFieldSqlDao.class));
        bind(BusinessInvoiceFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceFieldSqlDao>(BusinessInvoiceFieldSqlDao.class));
        bind(BusinessInvoiceItemSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceItemSqlDao>(BusinessInvoiceItemSqlDao.class));
        bind(BusinessInvoicePaymentFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentFieldSqlDao>(BusinessInvoicePaymentFieldSqlDao.class));
        bind(BusinessInvoicePaymentSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentSqlDao>(BusinessInvoicePaymentSqlDao.class));
        bind(BusinessInvoicePaymentTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoicePaymentTagSqlDao>(BusinessInvoicePaymentTagSqlDao.class));
        bind(BusinessInvoiceSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceSqlDao>(BusinessInvoiceSqlDao.class));
        bind(BusinessInvoiceTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessInvoiceTagSqlDao>(BusinessInvoiceTagSqlDao.class));
        bind(BusinessOverdueStatusSqlDao.class).toProvider(new BusinessSqlProvider<BusinessOverdueStatusSqlDao>(BusinessOverdueStatusSqlDao.class));
        bind(BusinessSubscriptionTransitionFieldSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionFieldSqlDao>(BusinessSubscriptionTransitionFieldSqlDao.class));
        bind(BusinessSubscriptionTransitionSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionSqlDao>(BusinessSubscriptionTransitionSqlDao.class));
        bind(BusinessSubscriptionTransitionTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessSubscriptionTransitionTagSqlDao>(BusinessSubscriptionTransitionTagSqlDao.class));
    }
}
