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

package com.ning.billing.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.mock.MockPlan;
import com.ning.billing.mock.MockSubscription;

import static com.ning.billing.catalog.api.Currency.USD;

public class TestBusinessSubscriptionTransition extends AnalyticsTestSuite {

    private BusinessSubscription prevSubscription;
    private BusinessSubscription nextSubscription;
    private BusinessSubscriptionEvent event;
    private DateTime requestedTimestamp;
    private Long totalOrdering;
    private UUID bundleId;
    private String externalKey;
    private UUID accountId;
    private String accountKey;
    private UUID subscriptionId;
    private BusinessSubscriptionTransitionModelDao transition;

    private final CatalogService catalogService = Mockito.mock(CatalogService.class);
    private final Catalog catalog = Mockito.mock(Catalog.class);

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        final Product product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        final Plan plan = new MockPlan("platinum-monthly", product);
        final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
        final Subscription prevISubscription = new MockSubscription(Subscription.SubscriptionState.ACTIVE, plan, phase);
        final Subscription nextISubscription = new MockSubscription(Subscription.SubscriptionState.CANCELLED, plan, phase);

        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);

        final DateTime now = new DateTime();

        prevSubscription = new BusinessSubscription(prevISubscription, USD, catalog);
        nextSubscription = new BusinessSubscription(nextISubscription, USD, catalog);
        event = BusinessSubscriptionEvent.subscriptionCancelled(prevISubscription.getCurrentPlan().getName(), catalog, now, now);
        requestedTimestamp = new DateTime(DateTimeZone.UTC);
        totalOrdering = 12L;
        bundleId = UUID.randomUUID();
        externalKey = "1234";
        accountId = UUID.randomUUID();
        accountKey = "pierre-1234";
        subscriptionId = UUID.randomUUID();
        transition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                                subscriptionId, requestedTimestamp, event, prevSubscription, nextSubscription);
    }

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        Assert.assertEquals(transition.getEvent(), event);
        Assert.assertEquals(transition.getPreviousSubscription(), prevSubscription);
        Assert.assertEquals(transition.getNextSubscription(), nextSubscription);
        Assert.assertEquals(transition.getRequestedTimestamp(), requestedTimestamp);
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        Assert.assertSame(transition, transition);
        Assert.assertEquals(transition, transition);
        Assert.assertTrue(transition.equals(transition));

        BusinessSubscriptionTransitionModelDao otherTransition;

        otherTransition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                                     subscriptionId, new DateTime(), event, prevSubscription, nextSubscription);
        Assert.assertTrue(!transition.equals(otherTransition));

        otherTransition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, "12345", accountId, accountKey,
                                                                     subscriptionId, requestedTimestamp, event, prevSubscription, nextSubscription);
        Assert.assertTrue(!transition.equals(otherTransition));

        otherTransition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                                     subscriptionId, requestedTimestamp, event, prevSubscription, prevSubscription);
        Assert.assertTrue(!transition.equals(otherTransition));

        otherTransition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                                     subscriptionId, requestedTimestamp, event, nextSubscription, nextSubscription);
        Assert.assertTrue(!transition.equals(otherTransition));

        otherTransition = new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                                     subscriptionId, requestedTimestamp, event, nextSubscription, prevSubscription);
        Assert.assertTrue(!transition.equals(otherTransition));
    }

    @Test(groups = "fast")
    public void testRejectInvalidTransitions() throws Exception {
        try {
            new BusinessSubscriptionTransitionModelDao(null, bundleId, externalKey, accountId, accountKey,
                                                       subscriptionId, requestedTimestamp, event, prevSubscription, nextSubscription);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        try {
            new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, null, accountId, accountKey,
                                                       subscriptionId, requestedTimestamp, event, prevSubscription, nextSubscription);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        try {
            new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                       subscriptionId, null, event, prevSubscription, nextSubscription);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        try {
            new BusinessSubscriptionTransitionModelDao(totalOrdering, bundleId, externalKey, accountId, accountKey,
                                                       subscriptionId, requestedTimestamp, null, prevSubscription, nextSubscription);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }
}
