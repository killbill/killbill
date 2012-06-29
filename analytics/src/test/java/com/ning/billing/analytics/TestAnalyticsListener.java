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

package com.ning.billing.analytics;

import javax.annotation.Nullable;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.mock.api.MockAccountUserApi;
import com.ning.billing.mock.api.MockEntitlementUserApi;
import com.ning.billing.util.callcontext.CallContext;

public class TestAnalyticsListener extends AnalyticsTestSuite {
    private static final String EXTERNAL_KEY = "1234";
    private static final String ACCOUNT_KEY = "pierre-1234";
    private final Currency CURRENCY = Currency.BRL;

    private final MockBusinessSubscriptionTransitionSqlDao dao = new MockBusinessSubscriptionTransitionSqlDao();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleUUID = UUID.randomUUID();
    private final Product product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
    private final Plan plan = new MockPlan("platinum-monthly", product);
    private final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
    private final PriceList priceList = null;

    private final CatalogService catalogService = Mockito.mock(CatalogService.class);
    private final Catalog catalog = Mockito.mock(Catalog.class);

    private UUID accountId;
    private AnalyticsListener listener;

    @BeforeClass(groups = "fast")
    public void setupCatalog() throws CatalogApiException {
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final MockAccountUserApi accountApi = new MockAccountUserApi();
        final Account accountData = new MockAccountBuilder().externalKey(ACCOUNT_KEY).currency(CURRENCY).build();
        final Account account = accountApi.createAccount(accountData, Mockito.mock(CallContext.class));
        accountId = account.getId();

        final MockEntitlementUserApi entitlementApi = new MockEntitlementUserApi();
        entitlementApi.addBundle(bundleUUID, EXTERNAL_KEY, accountId);

        final BusinessSubscriptionTransitionRecorder recorder = new BusinessSubscriptionTransitionRecorder(dao, catalogService, entitlementApi, accountApi);
        listener = new AnalyticsListener(recorder, null, null, null);
    }

    @Test(groups = "fast")
    public void testSubscriptionLifecycle() throws Exception {
        // Create a subscription
        final DateTime effectiveTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedTransitionTime = effectiveTransitionTime;
        final SubscriptionTransitionData firstTransition = createFirstSubscriptionTransition(requestedTransitionTime, effectiveTransitionTime);
        final BusinessSubscriptionTransition firstBST = createExpectedFirstBST(firstTransition.getTotalOrdering(), requestedTransitionTime, effectiveTransitionTime);
        listener.handleEffectiveSubscriptionTransitionChange(new DefaultEffectiveSubscriptionEvent(firstTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).size(), 1);
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).get(0), firstBST);

        // Cancel it
        final DateTime effectiveCancelTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedCancelTransitionTime = effectiveCancelTransitionTime;
        final SubscriptionTransitionData cancelledSubscriptionTransition = createCancelSubscriptionTransition(requestedCancelTransitionTime, effectiveCancelTransitionTime, firstTransition.getNextState());
        final BusinessSubscriptionTransition cancelledBST = createExpectedCancelledBST(cancelledSubscriptionTransition.getTotalOrdering(), requestedCancelTransitionTime, effectiveCancelTransitionTime, firstBST.getNextSubscription());
        listener.handleEffectiveSubscriptionTransitionChange(new DefaultEffectiveSubscriptionEvent(cancelledSubscriptionTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).size(), 2);
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).get(1), cancelledBST);

        // Recreate it
        final DateTime effectiveRecreatedTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedRecreatedTransitionTime = effectiveRecreatedTransitionTime;
        final SubscriptionTransitionData recreatedSubscriptionTransition = createRecreatedSubscriptionTransition(requestedRecreatedTransitionTime, effectiveRecreatedTransitionTime, cancelledSubscriptionTransition.getNextState());
        final BusinessSubscriptionTransition recreatedBST = createExpectedRecreatedBST(recreatedSubscriptionTransition.getTotalOrdering(), requestedRecreatedTransitionTime, effectiveRecreatedTransitionTime, cancelledBST.getNextSubscription());
        listener.handleEffectiveSubscriptionTransitionChange(new DefaultEffectiveSubscriptionEvent(recreatedSubscriptionTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).size(), 3);
        Assert.assertEquals(dao.getTransitions(EXTERNAL_KEY).get(2), recreatedBST);

    }

    private BusinessSubscriptionTransition createExpectedFirstBST(final Long totalOrdering, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime) {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCreated(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);

        final Subscription.SubscriptionState subscriptionState = Subscription.SubscriptionState.ACTIVE;
        return createExpectedBST(totalOrdering, event, requestedTransitionTime, effectiveTransitionTime, null, subscriptionState);
    }

    private BusinessSubscriptionTransition createExpectedCancelledBST(final Long totalOrdering, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final BusinessSubscription lastSubscription) {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);
        return createExpectedBST(totalOrdering, event, requestedTransitionTime, effectiveTransitionTime, lastSubscription, null);
    }

    private BusinessSubscriptionTransition createExpectedRecreatedBST(final Long totalOrdering, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final BusinessSubscription lastSubscription) {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionRecreated(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);
        final Subscription.SubscriptionState subscriptionState = Subscription.SubscriptionState.ACTIVE;
        return createExpectedBST(totalOrdering, event, requestedTransitionTime, effectiveTransitionTime, lastSubscription, subscriptionState);
    }

    private BusinessSubscriptionTransition createExpectedBST(
            final Long totalOrdering,
            final BusinessSubscriptionEvent eventType,
            final DateTime requestedTransitionTime,
            final DateTime effectiveTransitionTime,
            @Nullable final BusinessSubscription previousSubscription,
            @Nullable final Subscription.SubscriptionState nextState) {
        return new BusinessSubscriptionTransition(
                totalOrdering,
                EXTERNAL_KEY,
                ACCOUNT_KEY,
                requestedTransitionTime,
                eventType,
                previousSubscription,
                nextState == null ? null : new BusinessSubscription(
                        null,
                        plan.getName(),
                        phase.getName(),
                        CURRENCY,
                        effectiveTransitionTime,
                        nextState,
                        subscriptionId,
                        bundleUUID, catalog
                )
        );
    }

    private SubscriptionTransitionData createFirstSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime) {
        final ApiEventType eventType = ApiEventType.CREATE;
        final Subscription.SubscriptionState nextState = Subscription.SubscriptionState.ACTIVE;
        return new SubscriptionTransitionData(
                UUID.randomUUID(),
                subscriptionId,
                bundleUUID,
                EntitlementEvent.EventType.API_USER,
                eventType,
                requestedTransitionTime,
                effectiveTransitionTime,
                null,
                null,
                null,
                null,
                nextState,
                plan,
                phase,
                priceList,
                1L,
                null,
                true
        );
    }

    private SubscriptionTransitionData createCancelSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final Subscription.SubscriptionState previousState) {
        final ApiEventType eventType = ApiEventType.CANCEL;
        // next state is null for canceled events
        return new SubscriptionTransitionData(
                UUID.randomUUID(),
                subscriptionId,
                bundleUUID,
                EntitlementEvent.EventType.API_USER,
                eventType,
                requestedTransitionTime,
                effectiveTransitionTime,
                previousState,
                plan,
                phase,
                priceList,
                null,
                null,
                null,
                null,
                1L,
                null,
                true
        );
    }

    private SubscriptionTransitionData createRecreatedSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final Subscription.SubscriptionState previousState) {
        final ApiEventType eventType = ApiEventType.RE_CREATE;
        final Subscription.SubscriptionState nextState = Subscription.SubscriptionState.ACTIVE;
        return new SubscriptionTransitionData(
                UUID.randomUUID(),
                subscriptionId,
                bundleUUID,
                EntitlementEvent.EventType.API_USER,
                eventType,
                requestedTransitionTime,
                effectiveTransitionTime,
                previousState,
                null,
                null,
                null,
                nextState,
                plan,
                phase,
                priceList,
                1L,
                null,
                true
        );
    }
}
