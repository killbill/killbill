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

import org.joda.time.LocalDate;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
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
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestDefaultAnalyticsUserApi extends AnalyticsTestSuiteWithEmbeddedDB {

    private final Clock clock = new ClockMock();

    private AnalyticsUserApi analyticsUserApi;
    private BusinessAccountSqlDao accountSqlDao;
    private BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private BusinessInvoiceSqlDao invoiceSqlDao;
    private BusinessInvoiceItemSqlDao invoiceItemSqlDao;
    private BusinessAccountTagSqlDao accountTagSqlDao;
    private BusinessOverdueStatusSqlDao overdueStatusSqlDao;
    private BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        accountSqlDao = dbi.onDemand(BusinessAccountSqlDao.class);
        subscriptionTransitionSqlDao = dbi.onDemand(BusinessSubscriptionTransitionSqlDao.class);
        invoiceSqlDao = dbi.onDemand(BusinessInvoiceSqlDao.class);
        invoiceItemSqlDao = dbi.onDemand(BusinessInvoiceItemSqlDao.class);
        accountTagSqlDao = dbi.onDemand(BusinessAccountTagSqlDao.class);
        overdueStatusSqlDao = dbi.onDemand(BusinessOverdueStatusSqlDao.class);
        invoicePaymentSqlDao = dbi.onDemand(BusinessInvoicePaymentSqlDao.class);

        final AnalyticsDao analyticsDao = new DefaultAnalyticsDao(accountSqlDao, subscriptionTransitionSqlDao, invoiceSqlDao,
                                                                  invoiceItemSqlDao, accountTagSqlDao, overdueStatusSqlDao, invoicePaymentSqlDao);
        analyticsUserApi = new DefaultAnalyticsUserApi(analyticsDao);
    }

    @Test(groups = "slow")
    public void testAccountsCreatedOverTime() throws Exception {
        final BusinessAccount account = new BusinessAccount(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), BigDecimal.ONE, clock.getUTCToday(),
                                                            BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "FRANCE", "USD");
        accountSqlDao.createAccount(account);

        final TimeSeriesData data = analyticsUserApi.getAccountsCreatedOverTime();
        Assert.assertEquals(data.getDates().size(), 1);
        Assert.assertEquals(data.getDates().get(0), new LocalDate());
        Assert.assertEquals(data.getValues().size(), 1);
        Assert.assertEquals(data.getValues().get(0), (double) 1);
    }
}
