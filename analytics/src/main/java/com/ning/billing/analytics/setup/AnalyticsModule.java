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

package com.ning.billing.analytics.setup;


import com.google.inject.AbstractModule;
import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.analytics.BusinessAccountRecorder;
import com.ning.billing.analytics.BusinessSubscriptionTransitionRecorder;
import com.ning.billing.analytics.BusinessTagRecorder;
import com.ning.billing.analytics.api.AnalyticsService;
import com.ning.billing.analytics.api.DefaultAnalyticsService;
import com.ning.billing.analytics.api.user.DefaultAnalyticsUserApi;
import com.ning.billing.analytics.dao.AnalyticsDao;
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

public class AnalyticsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(BusinessAccountSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountSqlDao>(BusinessAccountSqlDao.class));
        bind(BusinessAccountTagSqlDao.class).toProvider(new BusinessSqlProvider<BusinessAccountTagSqlDao>(BusinessAccountTagSqlDao.class));
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

        bind(BusinessSubscriptionTransitionRecorder.class).asEagerSingleton();
        bind(BusinessAccountRecorder.class).asEagerSingleton();
        bind(BusinessTagRecorder.class).asEagerSingleton();
        bind(AnalyticsListener.class).asEagerSingleton();

        bind(AnalyticsDao.class).to(DefaultAnalyticsDao.class).asEagerSingleton();
        bind(AnalyticsService.class).to(DefaultAnalyticsService.class).asEagerSingleton();

        bind(DefaultAnalyticsUserApi.class).asEagerSingleton();
    }
}
