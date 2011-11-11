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

package com.ning.billing.analytics.dao;

import com.ning.billing.analytics.BusinessAccount;
import com.ning.billing.analytics.BusinessSubscription;
import com.ning.billing.analytics.BusinessSubscriptionEvent;
import com.ning.billing.analytics.BusinessSubscriptionTransition;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockPlan;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestAnalyticsDao
{
    private static final String EVENT_KEY = "12345";
    private static final String ACCOUNT_KEY = "pierre-143343-vcc";

    private final MysqlTestingHelper helper = new MysqlTestingHelper();

    private BusinessSubscriptionTransitionDao businessSubscriptionTransitionDao;
    private BusinessSubscriptionTransition transition;
    private BusinessAccountDao businessAccountDao;
    private BusinessAccount account;

    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException, ClassNotFoundException, SQLException
    {
        final String ddl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));

        helper.startMysql();
        helper.initDb(ddl);

        setupBusinessSubscriptionTransition();
        setupBusinessAccount();
    }

    private void setupBusinessSubscriptionTransition()
    {
        final IProduct product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        final IPlan plan = new MockPlan("platinum-monthly", product);
        final IPlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

        final BusinessSubscription prevSubscription = new BusinessSubscription(plan, phase, Currency.USD, new DateTime(DateTimeZone.UTC), ISubscription.SubscriptionState.ACTIVE, UUID.randomUUID(), UUID.randomUUID());
        final BusinessSubscription nextSubscription = new BusinessSubscription(plan, phase, Currency.USD, new DateTime(DateTimeZone.UTC), ISubscription.SubscriptionState.CANCELLED, UUID.randomUUID(), UUID.randomUUID());
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan);
        final DateTime requestedTimestamp = new DateTime(DateTimeZone.UTC);

        transition = new BusinessSubscriptionTransition(EVENT_KEY, requestedTimestamp, event, prevSubscription, nextSubscription);

        final IDBI dbi = helper.getDBI();
        businessSubscriptionTransitionDao = dbi.onDemand(BusinessSubscriptionTransitionDao.class);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            businessSubscriptionTransitionDao.test();
        }
        catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private void setupBusinessAccount()
    {
        final List<String> tags = new ArrayList<String>();
        tags.add("batch1");
        tags.add("great,guy");
        account = new BusinessAccount(ACCOUNT_KEY, BigDecimal.ONE, tags, new DateTime(DateTimeZone.UTC), BigDecimal.TEN, "CreditCard", "Visa", "FRANCE");

        final IDBI dbi = helper.getDBI();
        businessAccountDao = dbi.onDemand(BusinessAccountDao.class);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            businessAccountDao.test();
        }
        catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test(groups = "slow")
    public void testCreateAndRetrieveTransitions()
    {
        businessSubscriptionTransitionDao.createTransition(transition);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transition);

        Assert.assertEquals(businessSubscriptionTransitionDao.getTransitions("Doesn't exist").size(), 0);
    }

    @Test(groups = "slow")
    public void testCreateSaveAndRetrieveAccounts()
    {
        // Create and retrieve an account
        businessAccountDao.createAccount(account);
        final BusinessAccount foundAccount = businessAccountDao.getAccount(ACCOUNT_KEY);
        Assert.assertNotNull(foundAccount.getCreatedDt());
        Assert.assertEquals(foundAccount.getCreatedDt(), foundAccount.getUpdatedDt());
        // Verify the joiner stuff
        Assert.assertEquals(foundAccount.getTags().size(), 2);
        Assert.assertEquals(foundAccount.getTags().get(0), "batch1");
        Assert.assertEquals(foundAccount.getTags().get(1), "great,guy");
        // Verify the dates by backfilling them
        account.setCreatedDt(foundAccount.getCreatedDt());
        account.setUpdatedDt(foundAccount.getUpdatedDt());
        Assert.assertEquals(foundAccount, account);

        // Try to update the account
        final DateTime previousUpdatedDt = account.getUpdatedDt();
        account.setBalance(BigDecimal.TEN);
        account.setPaymentMethod("PayPal");
        businessAccountDao.saveAccount(account);
        // Verify the save worked as expected
        account = businessAccountDao.getAccount(ACCOUNT_KEY);
        Assert.assertEquals(Rounder.round(BigDecimal.TEN), account.getRoundedBalance());
        Assert.assertEquals("PayPal", account.getPaymentMethod());
        Assert.assertTrue(account.getUpdatedDt().compareTo(previousUpdatedDt) > 0);

        // Account not found
        Assert.assertNull(businessAccountDao.getAccount("Doesn't exist"));
    }
}
