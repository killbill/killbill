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

package com.ning.billing.analytics;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.api.DefaultAnalyticsService;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
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
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionFieldSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.analytics.glue.TestAnalyticsModuleNoDB;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class AnalyticsTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    @RealImplementation
    protected AccountUserApi accountApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected AnalyticsUserApi analyticsUserApi;
    @Inject
    protected CatalogService catalogService;
    @Inject
    @RealImplementation
    protected EntitlementUserApi entitlementApi;
    @Inject
    protected EntitlementInternalApi entitlementInternalApi;
    @Inject
    protected InvoiceUserApi invoiceApi;
    @Inject
    protected InvoiceDao realInvoiceDao;
    @Inject
    protected InvoiceInternalApi invoiceInternalApi;
    @Inject
    protected PaymentDao paymentDao;
    @Inject
    protected DefaultAnalyticsService service;
    @Inject
    protected InternalBus bus;
    @Inject
    protected BusinessAccountDao accountDao;
    @Inject
    protected BusinessAccountSqlDao accountSqlDao;
    @Inject
    protected BusinessAccountFieldSqlDao accountFieldSqlDao;
    @Inject
    protected BusinessAccountTagSqlDao accountTagSqlDao;
    @Inject
    protected BusinessInvoiceFieldSqlDao invoiceFieldSqlDao;
    @Inject
    protected BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    @Inject
    protected BusinessInvoicePaymentFieldSqlDao invoicePaymentFieldSqlDao;
    @Inject
    protected BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;
    @Inject
    protected BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao;
    @Inject
    protected BusinessInvoiceDao invoiceDao;
    @Inject
    protected BusinessInvoiceSqlDao invoiceSqlDao;
    @Inject
    protected BusinessInvoiceTagSqlDao invoiceTagSqlDao;
    @Inject
    protected BusinessOverdueStatusDao overdueStatusDao;
    @Inject
    protected BusinessOverdueStatusSqlDao overdueStatusSqlDao;
    @Inject
    protected BusinessSubscriptionTransitionFieldSqlDao subscriptionTransitionFieldSqlDao;
    @Inject
    protected BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao;
    @Inject
    protected BusinessSubscriptionTransitionDao subscriptionTransitionDao;
    @Inject
    protected BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    @Inject
    protected BusinessTagDao tagDao;

    @BeforeClass(groups = "fast")
    protected void setup() throws Exception {
        final Injector injector = Guice.createInjector(new TestAnalyticsModuleNoDB());
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void setupTest() {
        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void cleanupTest() {
        bus.stop();
    }
}
