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

package com.ning.billing.osgi.bundles.analytics.glue;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestNoDBModule;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.osgi.bundles.analytics.MockBusinessSubscriptionTransitionSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessAccountFieldSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoiceFieldSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoicePaymentFieldSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessOverdueStatusSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessSubscriptionTransitionFieldSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.util.bus.InMemoryBusModule;

public class TestAnalyticsModuleNoDB extends TestAnalyticsModule {

    public TestAnalyticsModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installAnalyticsSqlDao() {
        bind(BusinessSubscriptionTransitionSqlDao.class).to(MockBusinessSubscriptionTransitionSqlDao.class).asEagerSingleton();

        bind(BusinessAccountSqlDao.class).toInstance(Mockito.mock(BusinessAccountSqlDao.class));
        bind(BusinessAccountTagSqlDao.class).toInstance(Mockito.mock(BusinessAccountTagSqlDao.class));
        bind(BusinessAccountFieldSqlDao.class).toInstance(Mockito.mock(BusinessAccountFieldSqlDao.class));
        bind(BusinessInvoiceFieldSqlDao.class).toInstance(Mockito.mock(BusinessInvoiceFieldSqlDao.class));
        bind(BusinessInvoiceItemSqlDao.class).toInstance(Mockito.mock(BusinessInvoiceItemSqlDao.class));
        bind(BusinessInvoicePaymentFieldSqlDao.class).toInstance(Mockito.mock(BusinessInvoicePaymentFieldSqlDao.class));
        bind(BusinessInvoicePaymentSqlDao.class).toInstance(Mockito.mock(BusinessInvoicePaymentSqlDao.class));
        bind(BusinessInvoicePaymentTagSqlDao.class).toInstance(Mockito.mock(BusinessInvoicePaymentTagSqlDao.class));
        bind(BusinessInvoiceSqlDao.class).toInstance(Mockito.mock(BusinessInvoiceSqlDao.class));
        bind(BusinessInvoiceTagSqlDao.class).toInstance(Mockito.mock(BusinessInvoiceTagSqlDao.class));
        bind(BusinessOverdueStatusSqlDao.class).toInstance(Mockito.mock(BusinessOverdueStatusSqlDao.class));
        bind(BusinessSubscriptionTransitionFieldSqlDao.class).toInstance(Mockito.mock(BusinessSubscriptionTransitionFieldSqlDao.class));
        bind(BusinessSubscriptionTransitionTagSqlDao.class).toInstance(Mockito.mock(BusinessSubscriptionTransitionTagSqlDao.class));
    }

    @Override
    public void configure() {
        super.configure();

        install(new GuicyKillbillTestNoDBModule());
        install(new MockNonEntityDaoModule());
        install(new InMemoryBusModule(configSource));
    }
}
