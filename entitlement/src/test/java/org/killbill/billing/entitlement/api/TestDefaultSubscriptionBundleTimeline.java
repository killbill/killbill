/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestDefaultSubscriptionBundleTimeline extends EntitlementTestSuiteNoDB {

    private UUID bundleId;
    private String bundleExternalKey;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        bundleId = UUID.randomUUID();
        bundleExternalKey = bundleId.toString();
    }

    @Test(groups = "fast")
    public void testOneSimpleEntitlement() throws CatalogApiException {
        testOneSimpleEntitlementImpl(false);
    }

    @Test(groups = "fast")
    public void testOneSimpleEntitlementWithRegression() throws CatalogApiException {
        testOneSimpleEntitlementImpl(true);
    }

    private void testOneSimpleEntitlementImpl(boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final String externalKey = "foo";

        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
            blockingStates.add(bsCreate);
        }


        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase", null);
        allTransitions.add(tr3);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");
        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");
        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertNull(events.get(3).getNextPhase());
    }

    @Test(groups="fast")
    public void testOneSimpleEntitlementCancelImmediately() throws CatalogApiException {
        testOneSimpleEntitlementCancelImmediatelyImpl(false);
    }

    @Test(groups="fast")
    public void testOneSimpleEntitlementCancelImmediatelyWithRegression() throws CatalogApiException {
        testOneSimpleEntitlementCancelImmediatelyImpl(true);
    }

    private void testOneSimpleEntitlementCancelImmediatelyImpl(boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {

        clock.setDay(new LocalDate(2013, 1, 1));

        final UUID accountId = UUID.randomUUID();
        final String externalKey = "foo";

        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
            blockingStates.add(bsCreate);
        }

        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "trial", null);
        allTransitions.add(tr2);

        final BlockingState bsCancel = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
        blockingStates.add(bsCancel);



        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertNull(events.get(0).getPrevPhase());
        assertEquals(events.get(0).getNextPhase().getName(), "trial");

        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertNull(events.get(2).getNextPhase());

        assertEquals(events.get(3).getPrevPhase().getName(), "trial");
        assertNull(events.get(3).getNextPhase());
    }


    @Test(groups = "fast")
    public void testCancelBundleBeforeSubscription() throws CatalogApiException {
        testCancelBundleBeforeSubscriptionImpl(false);
    }
    @Test(groups = "fast")
    public void testCancelBundleBeforeSubscriptionWithRegression() throws CatalogApiException {
        testCancelBundleBeforeSubscriptionImpl(true);
    }

    private void testCancelBundleBeforeSubscriptionImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
            blockingStates.add(bsCreate);
        }

        // Block the bundle before the subscription
        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "trial", null);
        allTransitions.add(tr2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");
        assertEquals(events.get(3).getPrevPhase().getName(), "trial");
        assertNull(events.get(3).getNextPhase());
    }

    @Test(groups = "fast", description = "Test for https://github.com/killbill/killbill/issues/135")
    public void testOneEntitlementWithPauseResume() throws CatalogApiException {
        testOneEntitlementWithPauseResumeImpl(false);
    }

    @Test(groups = "fast", description = "Test for https://github.com/killbill/killbill/issues/135")
    public void testOneEntitlementWithPauseResumeWithRegression() throws CatalogApiException {
        testOneEntitlementWithPauseResumeImpl(true);
    }

    private void testOneEntitlementWithPauseResumeImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }


        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        effectiveDate = effectiveDate.plusDays(12);
        clock.addDays(12);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           "NothingUseful1", DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(42);
        clock.addDays(42);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           "NothingUseful2", DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final String service = "boo-service-which-will-pause-billing";
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           "NothingUseful3", service,
                                                           false, false, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 2L);

        blockingStates.add(bs3);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final BlockingState bs4 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           "NothingUseful4", service,
                                                           false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 3L);

        blockingStates.add(bs4);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 9);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(6).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(7).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(8).getEffectiveDate().compareTo(new LocalDate(bs4.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        assertEquals(events.get(6).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);

        assertEquals(events.get(7).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        assertEquals(events.get(8).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);

        assertEquals(events.get(3).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(4).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(5).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(6).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(7).getServiceName(), service);
        assertEquals(events.get(8).getServiceName(), service);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");

        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertEquals(events.get(3).getNextPhase().getName(), "phase");
        assertEquals(events.get(4).getPrevPhase().getName(), "phase");
        assertEquals(events.get(4).getNextPhase().getName(), "phase");
        assertEquals(events.get(5).getPrevPhase().getName(), "phase");
        assertEquals(events.get(5).getNextPhase().getName(), "phase");
        assertEquals(events.get(6).getPrevPhase().getName(), "phase");
        assertEquals(events.get(6).getNextPhase().getName(), "phase");
        assertEquals(events.get(7).getPrevPhase().getName(), "phase");
        assertEquals(events.get(7).getNextPhase().getName(), "phase");
        assertEquals(events.get(8).getPrevPhase().getName(), "phase");
        assertEquals(events.get(8).getNextPhase().getName(), "phase");
    }

    @Test(groups = "fast", description = "Test for https://github.com/killbill/killbill/issues/147 and https://github.com/killbill/killbill/issues/148")
    public void testOneEntitlementWithOverduePauseThenCancel() throws CatalogApiException {
        testOneEntitlementWithOverduePauseThenCancelImpl(false);
    }

    @Test(groups = "fast")
    public void testOneEntitlementWithOverduePauseThenCancelWithRegression() throws CatalogApiException {
        testOneEntitlementWithOverduePauseThenCancelImpl(true);
    }

    private void testOneEntitlementWithOverduePauseThenCancelImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        final String overdueService = "overdue-service";
        effectiveDate = effectiveDate.plusDays(12);
        clock.addDays(12);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           "ODE1", overdueService,
                                                           true, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(42);
        clock.addDays(42);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           "ODE2", overdueService,
                                                           true, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           "ODE3", overdueService,
                                                           true, true, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 2L);

        blockingStates.add(bs3);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final BlockingState bs4 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 3L);

        blockingStates.add(bs4);

        effectiveDate = effectiveDate.plusDays(1);
        clock.addDays(1);
        final BlockingState bs5 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           "ODE4", overdueService,
                                                           true, true, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 4L);

        blockingStates.add(bs5);
        // Note: cancellation event and ODE4 at the same effective date (see https://github.com/killbill/killbill/issues/148)
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase", null);
        allTransitions.add(tr3);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 10);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(6).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(7).getEffectiveDate().compareTo(new LocalDate(bs4.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(8).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(9).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);

        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(6).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);

        assertEquals(events.get(7).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);

        assertEquals(events.get(8).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        assertEquals(events.get(9).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);

        assertEquals(events.get(3).getServiceName(), overdueService);
        assertEquals(events.get(4).getServiceName(), overdueService);
        assertEquals(events.get(5).getServiceName(), overdueService);
        assertEquals(events.get(6).getServiceName(), overdueService);

        assertEquals(events.get(7).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);

        assertEquals(events.get(8).getServiceName(), overdueService);
        assertEquals(events.get(9).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");

        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertEquals(events.get(3).getNextPhase().getName(), "phase");
        assertEquals(events.get(4).getPrevPhase().getName(), "phase");
        assertEquals(events.get(4).getNextPhase().getName(), "phase");
        assertEquals(events.get(5).getPrevPhase().getName(), "phase");
        assertEquals(events.get(5).getNextPhase().getName(), "phase");
        assertEquals(events.get(6).getPrevPhase().getName(), "phase");
        assertEquals(events.get(6).getNextPhase().getName(), "phase");

        assertEquals(events.get(7).getPrevPhase().getName(), "phase");
        assertNull(events.get(7).getNextPhase());

        assertEquals(events.get(8).getPrevPhase().getName(), "phase");
        assertNull(events.get(8).getNextPhase());
        assertEquals(events.get(9).getPrevPhase().getName(), "phase");
        assertNull(events.get(9).getNextPhase());
    }

    @Test(groups = "fast")
    public void testOneEntitlementWithInitialBlockingState() throws CatalogApiException {
        testOneEntitlementWithInitialBlockingStateImpl(false);
    }

    @Test(groups = "fast")
    public void testOneEntitlementWithInitialBlockingStateWithRegression() throws CatalogApiException {
        testOneEntitlementWithInitialBlockingStateImpl(true);
    }

    private void testOneEntitlementWithInitialBlockingStateImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, clock.getUTCNow(), clock.getUTCNow(), clock.getUTCNow(), 0L);

        blockingStates.add(bs1);

        clock.addDays(1);

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        final String service = "boo";
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           "NothingUseful", service,
                                                           false, false, false, clock.getUTCNow(), clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase", null);
        allTransitions.add(tr3);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 5);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), service);
        assertEquals(events.get(4).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");
        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");
        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertEquals(events.get(3).getNextPhase().getName(), "phase");
        assertEquals(events.get(4).getPrevPhase().getName(), "phase");
        assertNull(events.get(4).getNextPhase());
    }

    @Test(groups = "fast")
    public void testOneEntitlementWithBlockingStatesSubscription() throws CatalogApiException {
        testOneEntitlementWithBlockingStatesSubscriptionImpl(false);
    }

    @Test(groups = "fast")
    public void testOneEntitlementWithBlockingStatesSubscriptionWithRegression() throws CatalogApiException {
        testOneEntitlementWithBlockingStatesSubscriptionImpl(true);
    }

    private void testOneEntitlementWithBlockingStatesSubscriptionImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        effectiveDate = effectiveDate.plusDays(5);
        clock.addDays(5);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase", null);
        allTransitions.add(tr3);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
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

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(4).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(5).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");

        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertEquals(events.get(3).getNextPhase().getName(), "phase");

        assertEquals(events.get(4).getPrevPhase().getName(), "phase");
        assertNull(events.get(4).getNextPhase());
        assertEquals(events.get(5).getPrevPhase().getName(), "phase");
        assertNull(events.get(5).getNextPhase());
    }

    @Test(groups = "fast")
    public void testWithMultipleEntitlements() throws CatalogApiException {
        testWithMultipleEntitlementsImpl(false);
    }

    @Test(groups = "fast")
    public void testWithMultipleEntitlementsWithRegression() throws CatalogApiException {
        testWithMultipleEntitlementsImpl(true);
    }

    private void testWithMultipleEntitlementsImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId1 = UUID.fromString("cf5a597a-cf15-45d3-8f02-95371be7f927");
        final UUID entitlementId2 = UUID.fromString("e37cc97a-7b98-4ab6-a29a-7259e45c3366");

        final List<SubscriptionBaseTransition> allTransitions1 = new ArrayList<SubscriptionBaseTransition>();
        final List<SubscriptionBaseTransition> allTransitions2 = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStatesEnt1 = new ArrayList<BlockingState>();
        final List<BlockingState> blockingStatesEnt2 = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition ent1Tr1 = createTransition(entitlementId1, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial1");
        allTransitions1.add(ent1Tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId1, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStatesEnt1.add(bsCreate);
        }

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent2Tr1 = createTransition(entitlementId2, EventType.API_USER, ApiEventType.TRANSFER, effectiveDate, clock.getUTCNow(), null, "phase2");
        allTransitions2.add(ent2Tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X ) {
            final BlockingState bsCreate2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId2, BlockingStateType.SUBSCRIPTION,
                                                                     DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStatesEnt2.add(bsCreate2);
        }

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent1Tr2 = createTransition(entitlementId1, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial1", "phase1");
        allTransitions1.add(ent1Tr2);

        effectiveDate = effectiveDate.plusDays(5);
        clock.addDays(5);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
        blockingStatesEnt1.add(bs1);
        blockingStatesEnt2.add(bs1);

        effectiveDate = effectiveDate.plusDays(15);
        clock.addDays(15);
        final SubscriptionBaseTransition ent1Tr3 = createTransition(entitlementId1, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase1", null);
        allTransitions1.add(ent1Tr3);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId1, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStatesEnt1.add(bs2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement1 = createEntitlement(entitlementId1, allTransitions1, blockingStatesEnt1);
        entitlements.add(entitlement1);

        final Entitlement entitlement2 = createEntitlement(entitlementId2, allTransitions2, blockingStatesEnt2);
        entitlements.add(entitlement2);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
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

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(4).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);

        assertEquals(events.get(5).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(6).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);

        assertEquals(events.get(7).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(8).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertEquals(events.get(0).getNextPhase().getName(), "trial1");

        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial1");

        assertNull(events.get(2).getPrevPhase());
        assertEquals(events.get(2).getNextPhase().getName(), "phase2");

        assertNull(events.get(3).getPrevPhase());
        assertEquals(events.get(3).getNextPhase().getName(), "phase2");

        assertEquals(events.get(4).getPrevPhase().getName(), "trial1");
        assertEquals(events.get(4).getNextPhase().getName(), "phase1");

        assertEquals(events.get(5).getPrevPhase().getName(), "phase1");
        assertEquals(events.get(5).getNextPhase().getName(), "phase1");

        assertEquals(events.get(6).getPrevPhase().getName(), "phase2");
        assertEquals(events.get(6).getNextPhase().getName(), "phase2");

        assertEquals(events.get(7).getPrevPhase().getName(), "phase1");
        assertNull(events.get(7).getNextPhase());

        assertEquals(events.get(8).getPrevPhase().getName(), "phase1");
        assertNull(events.get(8).getNextPhase());
    }

    @Test(groups = "fast")
    public void testWithOverdueOffline() throws CatalogApiException {
        testWithOverdueOfflineImpl(false);
    }

    @Test(groups = "fast")
    public void testWithOverdueOfflineWithRegression() throws CatalogApiException {
        testWithOverdueOfflineImpl(true);
    }

    private void testWithOverdueOfflineImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();

        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 23, 11, 8, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        effectiveDate = effectiveDate.plusDays(30);
        clock.addDays(30);
        final SubscriptionBaseTransition tr2 = createTransition(entitlementId, EventType.PHASE, null, effectiveDate, clock.getUTCNow(), "trial", "phase");
        allTransitions.add(tr2);

        effectiveDate = effectiveDate.plusDays(40); // 2013-03-12
        clock.addDays(40);
        final SubscriptionBaseTransition tr3 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CANCEL, effectiveDate, clock.getUTCNow(), "phase", null);
        allTransitions.add(tr3);

        // Verify the timeline without the blocking state events
        final ImmutableList<Entitlement> entitlementsWithoutBlockingStates = ImmutableList.<Entitlement>of(createEntitlement(entitlementId, allTransitions, blockingStates));
        final List<SubscriptionEvent> eventsWithoutBlockingStates = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlementsWithoutBlockingStates, internalCallContext).getSubscriptionEvents();
        assertEquals(eventsWithoutBlockingStates.size(), 4);
        assertEquals(eventsWithoutBlockingStates.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(eventsWithoutBlockingStates.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(eventsWithoutBlockingStates.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(eventsWithoutBlockingStates.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        final String service = "overdue-service";
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.ACCOUNT,
                                                           "OFFLINE", service,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

        blockingStates.add(bs1);

        // Verify the timeline with the overdue event blocking the entitlement
        final ImmutableList<Entitlement> entitlementsWithOverdueEvent = ImmutableList.<Entitlement>of(createEntitlement(entitlementId, allTransitions, blockingStates));
        final List<SubscriptionEvent> eventsWithOverdueEvent = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlementsWithOverdueEvent, internalCallContext).getSubscriptionEvents();
        assertEquals(eventsWithOverdueEvent.size(), 5);
        assertEquals(eventsWithOverdueEvent.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(eventsWithOverdueEvent.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(eventsWithOverdueEvent.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(eventsWithOverdueEvent.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(eventsWithOverdueEvent.get(4).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions,  blockingStates);
        entitlements.add(entitlement);

        // Verify the timeline with both the overdue event and the entitlement cancel event
        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        assertEquals(timeline.getAccountId(), accountId);
        assertEquals(timeline.getBundleId(), bundleId);
        assertEquals(timeline.getExternalKey(), externalKey);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 6);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(tr2.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(tr3.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), EntitlementOrderingBase.ENT_BILLING_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), service);
        assertEquals(events.get(4).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(5).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "phase");

        assertEquals(events.get(3).getPrevPhase().getName(), "phase");
        assertEquals(events.get(3).getNextPhase().getName(), "phase");
        assertEquals(events.get(4).getPrevPhase().getName(), "phase");
        assertNull(events.get(4).getNextPhase());

        assertEquals(events.get(5).getPrevPhase().getName(), "phase");
        assertNull(events.get(5).getNextPhase());
    }

    @Test(groups = "fast", description = "Test for https://github.com/killbill/killbill/issues/134")
    public void testRemoveOverlappingBlockingStates() throws CatalogApiException {
        testRemoveOverlappingBlockingStatesImpl(false);
    }

    @Test(groups = "fast")
    public void testRemoveOverlappingBlockingStatesWithRegression() throws CatalogApiException {
        testRemoveOverlappingBlockingStatesImpl(true);
    }

    public void testRemoveOverlappingBlockingStatesImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        // Overlapping ENT_STATE_BLOCKED - should merge
        effectiveDate = effectiveDate.plusDays(5);
        clock.addDays(5);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
        blockingStates.add(bs1);

        effectiveDate = effectiveDate.plusDays(1);
        clock.addDays(1);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);

        blockingStates.add(bs2);

        // Overlapping ENT_STATE_CANCELLED - should merge
        effectiveDate = effectiveDate.plusDays(1);
        clock.addDays(1);
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 2L);

        blockingStates.add(bs3);
        final BlockingState bs4 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 3L);

        blockingStates.add(bs4);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(2).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "trial");

        assertEquals(events.get(3).getPrevPhase().getName(), "trial");
        assertNull(events.get(3).getNextPhase());
    }

    @Test(groups = "fast", description = "Test for https://github.com/killbill/killbill/issues/149")
    public void testVariousBlockingStatesAtTheSameEffectiveDate() throws CatalogApiException {
        testVariousBlockingStatesAtTheSameEffectiveDateImpl(false);
    }

    @Test(groups = "fast")
    public void testVariousBlockingStatesAtTheSameEffectiveDateWithRegression() throws CatalogApiException {
        testVariousBlockingStatesAtTheSameEffectiveDateImpl(true);
    }

    private void testVariousBlockingStatesAtTheSameEffectiveDateImpl(final boolean regressionFlagForOlderVersionThan_0_17_X) throws CatalogApiException {
        clock.setDay(new LocalDate(2013, 1, 1));

        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = "foo";

        final UUID entitlementId = UUID.randomUUID();

        final List<SubscriptionBaseTransition> allTransitions = new ArrayList<SubscriptionBaseTransition>();
        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();

        DateTime effectiveDate = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final SubscriptionBaseTransition tr1 = createTransition(entitlementId, EventType.API_USER, ApiEventType.CREATE, effectiveDate, clock.getUTCNow(), null, "trial");
        allTransitions.add(tr1);

        if (!regressionFlagForOlderVersionThan_0_17_X) {
            final BlockingState bsCreate = new DefaultBlockingState(UUID.randomUUID(), entitlementId, BlockingStateType.SUBSCRIPTION,
                                                                    DefaultEntitlementApi.ENT_STATE_START, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                    false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);

            blockingStates.add(bsCreate);
        }

        // 2013-02-10
        effectiveDate = effectiveDate.plusDays(40);
        clock.addDays(40);
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 0L);
        blockingStates.add(bs1);
        // Same timestamp on purpose
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_CLEAR, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 1L);
        blockingStates.add(bs2);

        // 2013-02-20
        effectiveDate = effectiveDate.plusDays(10);
        clock.addDays(10);
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_BLOCKED, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           true, true, true, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 2L);
        blockingStates.add(bs3);

        // 2013-03-02
        effectiveDate = effectiveDate.plusDays(10);
        clock.addDays(10);
        final BlockingState bs4 = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                           DefaultEntitlementApi.ENT_STATE_CLEAR, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                           false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 3L);
        blockingStates.add(bs4);

        final String overdueService = "overdue-service";
        // 2013-03-04
        effectiveDate = effectiveDate.plusDays(2);
        clock.addDays(2);
        final BlockingState bs5 = new DefaultBlockingState(UUID.randomUUID(), accountId, BlockingStateType.ACCOUNT,
                                                           "OD1", overdueService,
                                                           false, false, false, effectiveDate, clock.getUTCNow(), clock.getUTCNow(), 4L);
        blockingStates.add(bs5);

        final List<Entitlement> entitlements = new ArrayList<Entitlement>();
        final Entitlement entitlement = createEntitlement(entitlementId, allTransitions, blockingStates);
        entitlements.add(entitlement);

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountId, bundleId, externalKey, entitlements, internalCallContext);

        final List<SubscriptionEvent> events = timeline.getSubscriptionEvents();
        assertEquals(events.size(), 11);

        assertEquals(events.get(0).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);
        assertEquals(events.get(1).getEffectiveDate().compareTo(new LocalDate(tr1.getEffectiveTransitionTime(), accountTimeZone)), 0);

        assertEquals(events.get(2).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(3).getEffectiveDate().compareTo(new LocalDate(bs1.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(4).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(5).getEffectiveDate().compareTo(new LocalDate(bs2.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(6).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(7).getEffectiveDate().compareTo(new LocalDate(bs3.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(8).getEffectiveDate().compareTo(new LocalDate(bs4.getEffectiveDate(), accountTimeZone)), 0);
        assertEquals(events.get(9).getEffectiveDate().compareTo(new LocalDate(bs4.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(10).getEffectiveDate().compareTo(new LocalDate(bs5.getEffectiveDate(), accountTimeZone)), 0);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        assertEquals(events.get(5).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);

        assertEquals(events.get(6).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        assertEquals(events.get(7).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        assertEquals(events.get(8).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        assertEquals(events.get(9).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);

        assertEquals(events.get(10).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);

        assertEquals(events.get(0).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(1).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(2).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(3).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(4).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(5).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(6).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(7).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);
        assertEquals(events.get(8).getServiceName(), DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertEquals(events.get(9).getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME);

        assertEquals(events.get(10).getServiceName(), overdueService);

        assertNull(events.get(0).getPrevPhase());
        assertNull(events.get(1).getPrevPhase());
        assertEquals(events.get(1).getNextPhase().getName(), "trial");

        assertEquals(events.get(2).getPrevPhase().getName(), "trial");
        assertEquals(events.get(2).getNextPhase().getName(), "trial");
        assertEquals(events.get(3).getPrevPhase().getName(), "trial");
        assertEquals(events.get(3).getNextPhase().getName(), "trial");
        assertEquals(events.get(4).getPrevPhase().getName(), "trial");
        assertEquals(events.get(4).getNextPhase().getName(), "trial");
        assertEquals(events.get(5).getPrevPhase().getName(), "trial");
        assertEquals(events.get(5).getNextPhase().getName(), "trial");

        assertEquals(events.get(6).getPrevPhase().getName(), "trial");
        assertEquals(events.get(6).getNextPhase().getName(), "trial");
        assertEquals(events.get(7).getPrevPhase().getName(), "trial");
        assertEquals(events.get(7).getNextPhase().getName(), "trial");
        assertEquals(events.get(8).getPrevPhase().getName(), "trial");
        assertEquals(events.get(8).getNextPhase().getName(), "trial");
        assertEquals(events.get(9).getPrevPhase().getName(), "trial");
        assertEquals(events.get(9).getNextPhase().getName(), "trial");

        assertEquals(events.get(10).getPrevPhase().getName(), "trial");
        assertEquals(events.get(10).getNextPhase().getName(), "trial");
    }

    private Entitlement createEntitlement(final UUID entitlementId, final List<SubscriptionBaseTransition> allTransitions, final Collection<BlockingState> blockingStates) {
        final DefaultEntitlement result = Mockito.mock(DefaultEntitlement.class);
        Mockito.when(result.getId()).thenReturn(entitlementId);

        final EventsStream eventsStream = Mockito.mock(EventsStream.class);
        Mockito.when(eventsStream.getBlockingStates()).thenReturn(blockingStates);
        Mockito.when(result.getEventsStream()).thenReturn(eventsStream);

        final SubscriptionBase base = Mockito.mock(SubscriptionBase.class);
        Mockito.when(base.getAllTransitions()).thenReturn(allTransitions);
        Mockito.when(result.getSubscriptionBase()).thenReturn(base);
        Mockito.when(result.getSubscriptionBase().getStartDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        return result;
    }

    private SubscriptionBaseTransition createTransition(final UUID entitlementId,
                                                        final EventType eventType,
                                                        final ApiEventType apiEventType,
                                                        final DateTime effectiveDate,
                                                        final DateTime createdDate,
                                                        final String prevPhaseName,
                                                        final String nextPhaseName
                                                       ) throws CatalogApiException {
        final PlanPhase prevPhase;
        final Plan prevPlan;
        final Product prevProduct;
        final PriceList prevPriceList;
        if (prevPhaseName == null) {
            prevPhase = null;
            prevPlan = null;
            prevProduct = null;
            prevPriceList = null;
        } else {
            prevPhase = Mockito.mock(PlanPhase.class);
            Mockito.when(prevPhase.getName()).thenReturn(prevPhaseName);

            prevProduct = Mockito.mock(Product.class);
            Mockito.when(prevProduct.getName()).thenReturn("product");

            prevPlan = Mockito.mock(Plan.class);
            Mockito.when(prevPlan.getName()).thenReturn("plan");
            Mockito.when(prevPlan.getProduct()).thenReturn(prevProduct);

            prevPriceList = Mockito.mock(PriceList.class);
            Mockito.when(prevPriceList.getName()).thenReturn("pricelist");
        }

        final PlanPhase nextPhase;
        final Plan nextPlan;
        final Product nextProduct;
        final PriceList nextPriceList;
        if (nextPhaseName == null) {
            nextPhase = null;
            nextPlan = null;
            nextProduct = null;
            nextPriceList = null;
        } else {
            nextPhase = Mockito.mock(PlanPhase.class);
            Mockito.when(nextPhase.getName()).thenReturn(nextPhaseName);

            nextProduct = Mockito.mock(Product.class);
            Mockito.when(nextProduct.getName()).thenReturn("product");

            nextPlan = Mockito.mock(Plan.class);
            Mockito.when(nextPlan.getName()).thenReturn("plan");
            Mockito.when(nextPlan.getProduct()).thenReturn(nextProduct);

            nextPriceList = Mockito.mock(PriceList.class);
            Mockito.when(nextPriceList.getName()).thenReturn("pricelist");
        }

        return new SubscriptionBaseTransitionData(UUID.randomUUID(),
                                                  entitlementId,
                                                  bundleId,
                                                  bundleExternalKey,
                                                  eventType,
                                                  apiEventType,
                                                  effectiveDate,
                                                  null,
                                                  null,
                                                  null,
                                                  prevPlan,
                                                  prevPhase,
                                                  prevPriceList,
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  nextPlan,
                                                  nextPhase,
                                                  nextPriceList,
                                                  null,
                                                  1L,
                                                  createdDate,
                                                  UUID.randomUUID(),
                                                  true);
    }
}
