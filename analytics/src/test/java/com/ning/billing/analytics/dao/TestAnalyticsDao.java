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

package com.ning.billing.analytics.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.mock.MockPlan;

public class TestAnalyticsDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private static final Long TOTAL_ORDERING = 1L;
    private static final UUID BUNDLE_ID = UUID.randomUUID();
    private static final String EXTERNAL_KEY = "23456";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ACCOUNT_KEY = "pierre-143343-vcc";
    private static final String CURRENCY = UUID.randomUUID().toString();

    private final Product product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
    private final Plan plan = new MockPlan("platinum-monthly", product);
    private final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

    private BusinessSubscriptionTransitionModelDao transition;
    private BusinessAccountModelDao account;

    private final Catalog catalog = Mockito.mock(Catalog.class);

    @BeforeClass(groups = "slow")
    public void setupMocks() throws IOException, ClassNotFoundException, SQLException, CatalogApiException {
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);

        setupBusinessSubscriptionTransition();
        setupBusinessAccount();
    }

    private void setupBusinessSubscriptionTransition() {
        final DateTime requestedTimestamp = clock.getUTCNow();
        final BusinessSubscription prevSubscription = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, clock.getUTCNow(), Subscription.SubscriptionState.ACTIVE, catalog);
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, clock.getUTCNow(), Subscription.SubscriptionState.CANCELLED, catalog);
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan.getName(), catalog, requestedTimestamp, requestedTimestamp);

        transition = new BusinessSubscriptionTransitionModelDao(TOTAL_ORDERING, BUNDLE_ID, EXTERNAL_KEY, ACCOUNT_ID, ACCOUNT_KEY,
                                                                UUID.randomUUID(), requestedTimestamp, event, prevSubscription, nextSubscription);
    }

    private void setupBusinessAccount() {
        account = new BusinessAccountModelDao(UUID.randomUUID(), ACCOUNT_KEY, UUID.randomUUID().toString(), BigDecimal.ONE, clock.getUTCToday(),
                                              BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "FRANCE", CURRENCY, clock.getUTCNow(), clock.getUTCNow());
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPrevSubscription() {
        final BusinessSubscriptionTransitionModelDao transitionWithNullPrev = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                null,
                transition.getNextSubscription()
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullPrev, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullPrev);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullNextSubscription() {
        final BusinessSubscriptionTransitionModelDao transitionWithNullNext = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                transition.getPreviousSubscription(),
                null
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullNext, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullNext);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullFieldsInSubscription() {
        final BusinessSubscription subscriptionWithNullFields = new BusinessSubscription(null, plan.getName(), phase.getName(), Currency.USD, null, null, catalog);
        final BusinessSubscriptionTransitionModelDao transitionWithNullFields = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                subscriptionWithNullFields,
                subscriptionWithNullFields
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullFields, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transitionWithNullFields);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPlanAndPhase() throws Exception {
        final BusinessSubscription subscriptionWithNullPlanAndPhase = new BusinessSubscription(null, null, null, Currency.USD, null, null, catalog);
        final BusinessSubscriptionTransitionModelDao transitionWithNullPlanAndPhase = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                subscriptionWithNullPlanAndPhase,
                subscriptionWithNullPlanAndPhase
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullPlanAndPhase, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0).getExternalKey(), transition.getExternalKey());
        Assert.assertEquals(transitions.get(0).getRequestedTimestamp(), transition.getRequestedTimestamp());
        Assert.assertEquals(transitions.get(0).getEvent(), transition.getEvent());
        Assert.assertNull(transitions.get(0).getPreviousSubscription());
        Assert.assertNull(transitions.get(0).getNextSubscription());
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPlan() throws Exception {
        final BusinessSubscription subscriptionWithNullPlan = new BusinessSubscription(null, null, phase.getName(), Currency.USD, null, null, catalog);
        final BusinessSubscriptionTransitionModelDao transitionWithNullPlan = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                subscriptionWithNullPlan,
                subscriptionWithNullPlan
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullPlan, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        // Null Plan but Phase - we don't turn the subscription into a null
        Assert.assertEquals(transitions.get(0), transitionWithNullPlan);
    }

    @Test(groups = "slow")
    public void testTransitionsWithNullPhase() throws Exception {
        final BusinessSubscription subscriptionWithNullPhase = new BusinessSubscription(null, plan.getName(), null, Currency.USD, null, null, catalog);
        final BusinessSubscriptionTransitionModelDao transitionWithNullPhase = new BusinessSubscriptionTransitionModelDao(
                transition.getTotalOrdering(),
                transition.getBundleId(),
                transition.getExternalKey(),
                transition.getAccountId(),
                transition.getAccountKey(),
                transition.getSubscriptionId(),
                transition.getRequestedTimestamp(),
                transition.getEvent(),
                subscriptionWithNullPhase,
                subscriptionWithNullPhase
        );
        subscriptionTransitionSqlDao.createTransition(transitionWithNullPhase, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0).getExternalKey(), transition.getExternalKey());
        Assert.assertEquals(transitions.get(0).getRequestedTimestamp(), transition.getRequestedTimestamp());
        Assert.assertEquals(transitions.get(0).getEvent(), transition.getEvent());

        // Null Phase but Plan - we don't turn the subscription into a null, however price and mrr are both set to 0 (not null)
        final BusinessSubscription blankSubscription = new BusinessSubscription(null, plan.getName(), new MockPhase(null, null, null, 0.0).getName(), Currency.USD, null, null, catalog);
        Assert.assertEquals(transitions.get(0).getPreviousSubscription(), blankSubscription);
        Assert.assertEquals(transitions.get(0).getNextSubscription(), blankSubscription);
    }

    @Test(groups = "slow")
    public void testCreateAndRetrieveTransitions() {
        subscriptionTransitionSqlDao.createTransition(transition, internalCallContext);

        final List<BusinessSubscriptionTransitionModelDao> transitions = subscriptionTransitionSqlDao.getTransitionsByKey(EXTERNAL_KEY, internalCallContext);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transition);

        Assert.assertEquals(subscriptionTransitionSqlDao.getTransitionsByKey("Doesn't exist", internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testCreateSaveAndRetrieveAccounts() {
        // Create and retrieve an account
        accountSqlDao.createAccount(account, internalCallContext);
        final BusinessAccountModelDao foundAccount = accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext);
        Assert.assertEquals(foundAccount.getCreatedDate().getMillis(), account.getCreatedDate().getMillis());
        Assert.assertEquals(foundAccount.getUpdatedDate().getMillis(), account.getUpdatedDate().getMillis());
        Assert.assertTrue(foundAccount.equals(account));

        // Try to update the account
        account.setBalance(BigDecimal.TEN);
        account.setPaymentMethod("PayPal");
        account.setCurrency("CAD");
        accountSqlDao.saveAccount(account, internalCallContext);
        // Verify the save worked as expected
        account = accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext);
        Assert.assertEquals(Rounder.round(BigDecimal.TEN), account.getRoundedBalance());
        Assert.assertEquals("PayPal", account.getPaymentMethod());
        Assert.assertEquals("CAD", account.getCurrency());

        // ACCOUNT not found
        Assert.assertNull(accountSqlDao.getAccountByKey("Doesn't exist", internalCallContext));
    }
}
