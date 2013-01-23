/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.BusinessAccountDao;
import com.ning.billing.analytics.BusinessInvoiceDao;
import com.ning.billing.analytics.BusinessInvoicePaymentDao;
import com.ning.billing.analytics.BusinessOverdueStatusDao;
import com.ning.billing.analytics.BusinessSubscriptionTransitionDao;
import com.ning.billing.analytics.BusinessTagDao;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.analytics.dao.AnalyticsDao;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceItemSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.dao.BusinessOverdueStatusSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.dao.DefaultAnalyticsDao;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.mock.MockPlan;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.payment.PaymentInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;

public class TestDefaultAnalyticsUserApi extends AnalyticsTestSuiteWithEmbeddedDB {

    private final Clock clock = new ClockMock();
    private final TenantContext tenantContext = Mockito.mock(TenantContext.class);

    private AnalyticsUserApi analyticsUserApi;
    private BusinessAccountSqlDao accountSqlDao;
    private BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private BusinessInvoiceSqlDao invoiceSqlDao;
    private BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    private BusinessAccountTagSqlDao accountTagSqlDao;
    private BusinessOverdueStatusSqlDao overdueStatusSqlDao;
    private BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;

    @BeforeMethod(groups = "mysql")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        final NonEntityDao nonEntityDao = new DefaultNonEntityDao(dbi);
        accountSqlDao = dbi.onDemand(BusinessAccountSqlDao.class);
        subscriptionTransitionSqlDao = dbi.onDemand(BusinessSubscriptionTransitionSqlDao.class);
        invoiceSqlDao = dbi.onDemand(BusinessInvoiceSqlDao.class);
        invoiceItemSqlDao = dbi.onDemand(BusinessInvoiceItemSqlDao.class);
        accountTagSqlDao = dbi.onDemand(BusinessAccountTagSqlDao.class);
        overdueStatusSqlDao = dbi.onDemand(BusinessOverdueStatusSqlDao.class);
        invoicePaymentSqlDao = dbi.onDemand(BusinessInvoicePaymentSqlDao.class);

        final AnalyticsDao analyticsDao = new DefaultAnalyticsDao(accountSqlDao, subscriptionTransitionSqlDao, invoiceSqlDao,
                                                                  invoiceItemSqlDao, accountTagSqlDao, overdueStatusSqlDao, invoicePaymentSqlDao);
        analyticsUserApi = new DefaultAnalyticsUserApi(analyticsDao,
                                                       Mockito.mock(BusinessSubscriptionTransitionDao.class),
                                                       Mockito.mock(BusinessAccountDao.class),
                                                       Mockito.mock(BusinessInvoiceDao.class),
                                                       Mockito.mock(BusinessOverdueStatusDao.class),
                                                       Mockito.mock(BusinessInvoicePaymentDao.class),
                                                       Mockito.mock(BusinessTagDao.class),
                                                       Mockito.mock(EntitlementInternalApi.class),
                                                       Mockito.mock(PaymentInternalApi.class),
                                                       Mockito.mock(TagInternalApi.class),
                                                       new InternalCallContextFactory(clock, nonEntityDao, new CacheControllerDispatcher()));
    }

    @Test(groups = "mysql")
    public void testAccountsCreatedOverTime() throws Exception {
        final BusinessAccountModelDao account = new BusinessAccountModelDao(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), BigDecimal.ONE, clock.getUTCToday(),
                                                                            BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "FRANCE", "USD", clock.getUTCNow(), clock.getUTCNow());
        accountSqlDao.createAccount(account, internalCallContext);

        final TimeSeriesData data = analyticsUserApi.getAccountsCreatedOverTime(tenantContext);
        Assert.assertEquals(data.getDates().size(), 1);
        Assert.assertEquals(data.getDates().get(0), clock.getUTCToday());
        Assert.assertEquals(data.getValues().size(), 1);
        Assert.assertEquals(data.getValues().get(0), (double) 1);
    }

    @Test(groups = "mysql")
    public void testSubscriptionsCreatedOverTime() throws Exception {
        final String productType = "subscription";
        final Product product = new MockProduct("platinum", productType, ProductCategory.BASE);
        final Plan plan = new MockPlan("platinum-monthly", product);
        final PlanPhase phase = new MockPhase(PhaseType.TRIAL, plan, MockDuration.UNLIMITED(), 25.95);
        final Catalog catalog = Mockito.mock(Catalog.class);
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);
        final BusinessSubscriptionTransitionModelDao transition = new BusinessSubscriptionTransitionModelDao(
                3L,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                clock.getUTCNow(),
                BusinessSubscriptionEvent.subscriptionCreated(plan.getName(), catalog, clock.getUTCNow(), clock.getUTCNow()),
                null,
                new BusinessSubscription("DEFAULT", plan.getName(), phase.getName(), Currency.USD, clock.getUTCNow(), Subscription.SubscriptionState.ACTIVE, catalog)
        );
        subscriptionTransitionSqlDao.createTransition(transition, internalCallContext);

        final TimeSeriesData notFoundData = analyticsUserApi.getSubscriptionsCreatedOverTime(productType, UUID.randomUUID().toString(), tenantContext);
        Assert.assertEquals(notFoundData.getDates().size(), 0);
        Assert.assertEquals(notFoundData.getValues().size(), 0);

        final TimeSeriesData data = analyticsUserApi.getSubscriptionsCreatedOverTime(productType, phase.getName(), tenantContext);
        Assert.assertEquals(data.getDates().size(), 1);
        Assert.assertEquals(data.getDates().get(0), clock.getUTCToday());
        Assert.assertEquals(data.getValues().size(), 1);
        Assert.assertEquals(data.getValues().get(0), (double) 1);
    }
}
