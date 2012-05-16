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
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestAnalyticsDao
{
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String EVENT_KEY = "23456";
    private static final String ACCOUNT_KEY = "pierre-143343-vcc";

    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private final Product product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
    private final Plan plan = new MockPlan("platinum-monthly", product);
    private final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

    private BusinessSubscriptionTransitionDao businessSubscriptionTransitionDao;
    private BusinessSubscriptionTransition transition;
    private BusinessAccountDao businessAccountDao;
    private BusinessAccount account;

    private final CatalogService catalogService = BrainDeadProxyFactory.createBrainDeadProxyFor(CatalogService.class);
    private final Catalog catalog = BrainDeadProxyFactory.createBrainDeadProxyFor(Catalog.class);
    
    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException, ClassNotFoundException, SQLException
    {

        ((ZombieControl) catalog).addResult("findPlan", plan);
        ((ZombieControl) catalog).addResult("findPhase", phase);        
        ((ZombieControl) catalogService).addResult("getFullCatalog", catalog); 
        
        final String ddl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));

        helper.startMysql();
        helper.initDb(ddl);

        setupBusinessSubscriptionTransition();
        setupBusinessAccount();
    }

    private void setupBusinessSubscriptionTransition()
    {
        final DateTime requestedTimestamp = new DateTime(DateTimeZone.UTC);
        final BusinessSubscription prevSubscription = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, new DateTime(DateTimeZone.UTC), Subscription.SubscriptionState.ACTIVE, UUID.randomUUID(), UUID.randomUUID(), catalog);
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, new DateTime(DateTimeZone.UTC), Subscription.SubscriptionState.CANCELLED, UUID.randomUUID(), UUID.randomUUID(), catalog);
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan.getName(), catalog, requestedTimestamp, requestedTimestamp);
        

        transition = new BusinessSubscriptionTransition(EVENT_ID, EVENT_KEY, ACCOUNT_KEY, requestedTimestamp, event, prevSubscription, nextSubscription);

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
        account = new BusinessAccount(ACCOUNT_KEY, BigDecimal.ONE, tags, new DateTime(DateTimeZone.UTC), BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "FRANCE");

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

    @AfterClass(groups = "slow")
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @BeforeMethod(groups = "slow")
    public void cleanup() throws Exception
    {
        helper.cleanupTable("bst");
        helper.cleanupTable("bac");
    }

    @Test(groups = "slow")
    public void testHandleDuplicatedEvents()
    {
        final BusinessSubscriptionTransition transitionWithNullPrev = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            null,
            transition.getNextSubscription()
        );

        businessSubscriptionTransitionDao.createTransition(transitionWithNullPrev);
        List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullPrev);
        // Try to add the same transition, with the same UUID - we should only store one though
        businessSubscriptionTransitionDao.createTransition(transitionWithNullPrev);
        transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullPrev);

        // Try now to store a look-alike transition (same fields except UUID) - we should store it this time
        final BusinessSubscriptionTransition secondTransitionWithNullPrev = new BusinessSubscriptionTransition(
            UUID.randomUUID(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            null,
            transition.getNextSubscription()
        );
        businessSubscriptionTransitionDao.createTransition(secondTransitionWithNullPrev);
        transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 2);
        Assert.assertTrue(transitions.contains(transitionWithNullPrev));
        Assert.assertTrue(transitions.contains(secondTransitionWithNullPrev));
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPrevSubscription()
    {
        final BusinessSubscriptionTransition transitionWithNullPrev = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            null,
            transition.getNextSubscription()
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullPrev);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullPrev);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullNextSubscription()
    {
        final BusinessSubscriptionTransition transitionWithNullNext = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            transition.getPreviousSubscription(),
            null
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullNext);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullNext);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullFieldsInSubscription()
    {
        final BusinessSubscription subscriptionWithNullFields = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, null, null, null, null, catalog);
        final BusinessSubscriptionTransition transitionWithNullFields = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            subscriptionWithNullFields,
            subscriptionWithNullFields
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullFields);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullFields);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPlanAndPhase() throws Exception
    {
        final BusinessSubscription subscriptionWithNullPlanAndPhase = new BusinessSubscription(null, null, null, Currency.USD, null, null, null, null, catalog);
        final BusinessSubscriptionTransition transitionWithNullPlanAndPhase = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            subscriptionWithNullPlanAndPhase,
            subscriptionWithNullPlanAndPhase
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullPlanAndPhase);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0).getKey(), transition.getKey());
        Assert.assertEquals(transitions.get(0).getRequestedTimestamp(), transition.getRequestedTimestamp());
        Assert.assertEquals(transitions.get(0).getEvent(), transition.getEvent());
        Assert.assertNull(transitions.get(0).getPreviousSubscription());
        Assert.assertNull(transitions.get(0).getNextSubscription());
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPlan() throws Exception
    {
        final BusinessSubscription subscriptionWithNullPlan = new BusinessSubscription(null, null, phase.getName(), Currency.USD, null, null, null, null, catalog);
        final BusinessSubscriptionTransition transitionWithNullPlan = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            subscriptionWithNullPlan,
            subscriptionWithNullPlan
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullPlan);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        // Null Plan but Phase - we don't turn the subscription into a null
        Assert.assertEquals(transitions.get(0), transitionWithNullPlan);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPhase() throws Exception
    {
        final BusinessSubscription subscriptionWithNullPhase = new BusinessSubscription(null, plan.getName(), null, Currency.USD, null, null, null, null, catalog);
        final BusinessSubscriptionTransition transitionWithNullPhase = new BusinessSubscriptionTransition(
            transition.getId(),
            transition.getKey(),
            transition.getAccountKey(),
            transition.getRequestedTimestamp(),
            transition.getEvent(),
            subscriptionWithNullPhase,
            subscriptionWithNullPhase
        );
        businessSubscriptionTransitionDao.createTransition(transitionWithNullPhase);

        final List<BusinessSubscriptionTransition> transitions = businessSubscriptionTransitionDao.getTransitions(EVENT_KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0).getKey(), transition.getKey());
        Assert.assertEquals(transitions.get(0).getRequestedTimestamp(), transition.getRequestedTimestamp());
        Assert.assertEquals(transitions.get(0).getEvent(), transition.getEvent());

        // Null Phase but Plan - we don't turn the subscription into a null, however price and mrr are both set to 0 (not null)
        final BusinessSubscription blankSubscription = new BusinessSubscription(null, plan.getName(), new MockPhase(null, null, null, 0.0).getName(), Currency.USD, null, null, null, null, catalog);
        Assert.assertEquals(transitions.get(0).getPreviousSubscription(), blankSubscription);
        Assert.assertEquals(transitions.get(0).getNextSubscription(), blankSubscription);
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

        // ACCOUNT not found
        Assert.assertNull(businessAccountDao.getAccount("Doesn't exist"));
    }
}
