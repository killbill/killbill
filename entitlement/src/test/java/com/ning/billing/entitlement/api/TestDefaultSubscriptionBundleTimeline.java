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

package com.ning.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransitionData;
import com.ning.billing.subscription.events.SubscriptionBaseEvent.EventType;
import com.ning.billing.subscription.events.user.ApiEventType;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

import static org.testng.Assert.assertEquals;

public class TestDefaultSubscriptionBundleTimeline extends EntitlementTestSuiteNoDB {

    private UUID bundleId;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        super.beforeClass();
        bundleId = UUID.randomUUID();
    }

    @Test(groups = "fast")
    public void testOneEntitlementNoBlockingStates() throws CatalogApiException {

        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";


        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();

        final DateTime requestedDate = new DateTime();
        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr1);

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr2);


        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr3);


        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions);
        entitlements.add(entitlement);

        final DefaultSubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountTimeZone, accountId, bundleId, externalKey, entitlements, Collections.<BlockingState>emptyList());

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
    }


    @Test(groups = "fast")
    public void testOneEntitlementWithBlockingStatesSubscription() throws CatalogApiException {

        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";


        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        final DateTime requestedDate = new DateTime();
        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr1);

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr2);

        effectiveDate = effectiveDate.plusDays(5);
        clock.addDays(5);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow());
        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions.add(tr3);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow());

        blockingStates.add(bs2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions);
        entitlements.add(entitlement);


        final DefaultSubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountTimeZone, accountId, bundleId, externalKey, entitlements, blockingStates);

        List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 6);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
    }


    @Test(groups = "fast")
    public void testWithMultipleEntitlements() throws CatalogApiException {

        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";


        final UUID entitlementId1 = UUID.randomUUID();
        final UUID entitlementId2 = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions1 = new ArrayList<SubscriptionBaseTransition>();
        final List<SubscriptionBaseTransition> allTransitions2 = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        final DateTime requestedDate = new DateTime();
        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition ent1Tr1 = createTransition(entitlementId1, EventType.API_USER, ApiEventType.CREATE, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions1.add(ent1Tr1);


        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent2Tr1 = createTransition(entitlementId2, EventType.API_USER, ApiEventType.TRANSFER, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions2.add(ent2Tr1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent1Tr2 = createTransition(entitlementId1, EventType.PHASE, null, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions1.add(ent1Tr2);

        effectiveDate = effectiveDate.plusDays(5);
        clock.addDays(5);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow());
        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent1Tr3 = createTransition(entitlementId1, EventType.API_USER, ApiEventType.CANCEL, requestedDate, effectiveDate, clock.getUTCNow());
        allTransitions1.add(ent1Tr3);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId1, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow());

        blockingStates.add(bs2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement1 = createEntitlement(entitlementId1, allTransitions1);
        entitlements.add(entitlement1);

        final Entitlement entitlement2 = createEntitlement(entitlementId2, allTransitions2);
        entitlements.add(entitlement2);


        final DefaultSubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountTimeZone, accountId, bundleId, externalKey, entitlements, blockingStates);

        List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 9);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(ent1Tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(ent1Tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(ent2Tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(ent2Tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(ent1Tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(6).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(7).getEffectiveDate().compareTo(new LocalDate(ent1Tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(8).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(6).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);

        assertEquals(events.get(7).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(8).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
    }


    private DefaultEntitlement createEntitlement(final UUID entitlementId, final List<SubscriptionBaseTransition> allTransitions) {

        final DefaultEntitlement result = Mockito.mock(DefaultEntitlement.class);
        Mockito.when(result.getId()).thenReturn(entitlementId);

        final SubscriptionBase base = Mockito.mock(SubscriptionBase.class);
        Mockito.when(base.getAllTransitions()).thenReturn(allTransitions);
        Mockito.when(result.getSubscriptionBase()).thenReturn(base);
        return result;
    }

    private SubscriptionBaseTransition createTransition(final UUID entitlementId,
                                                        final EventType eventType,
                                                        final ApiEventType apiEventType,
                                                        final DateTime requestedDate,
                                                        final DateTime effectiveDate,
                                                        final DateTime createdDate
                                                       ) throws CatalogApiException {


        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        Mockito.when(phase.getName()).thenReturn("phase");

        //catalogService.getCurrentCatalog().findCurrentPhase("pistol-monthly-trial");
        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("plan");


        //catalogService.getCurrentCatalog().findCurrentPlan("pistol-monthly");
        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn("product");

        //catalogService.getCurrentCatalog().findCurrentProduct("Pistol");

        final PriceList priceList = Mockito.mock(PriceList.class);
        Mockito.when(priceList.getName()).thenReturn("pricelist");

        //catalogService.getCurrentCatalog().findCurrentPricelist(PriceListSet.DEFAULT_PRICELIST_NAME);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

        final SubscriptionBaseTransition transition = new SubscriptionBaseTransitionData(UUID.randomUUID(),
                                                                                         entitlementId,
                                                                                         bundleId,
                                                                                         eventType,
                                                                                         apiEventType,
                                                                                         requestedDate,
                                                                                         effectiveDate,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         plan,
                                                                                         phase,
                                                                                         priceList,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         plan,
                                                                                         phase,
                                                                                         priceList,
                                                                                         1L,
                                                                                         createdDate,
                                                                                         UUID.randomUUID(),
                                                                                         true);
        return transition;
    }
}
