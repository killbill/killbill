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
package com.ning.billing.beatrix.integration;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEvents;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestRepairIntegration extends TestIntegrationBase {


    @Test(groups = {"slow"}, enabled = false)
    public void testRepairChangeBPWithAddonIncludedIntrial() throws Exception {
        log.info("Starting testRepairChangeBPWithAddonIncludedIntrial");
        testRepairChangeBPWithAddonIncluded(true);
    }

    @Test(groups = {"slow"}, enabled = false)
    public void testRepairChangeBPWithAddonIncludedOutOfTrial() throws Exception {
        log.info("Starting testRepairChangeBPWithAddonIncludedOutOfTrial");
        testRepairChangeBPWithAddonIncluded(false);
    }

    private void testRepairChangeBPWithAddonIncluded(final boolean inTrial) throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithPaymentMethod(getAccountData(25));

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        final SubscriptionData aoSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                 new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, callContext));
        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        final SubscriptionData aoSubscription2 = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                  new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, callContext));
        assertTrue(busHandler.isCompleted(DELAY));


        // MOVE CLOCK A LITTLE BIT MORE -- EITHER STAY IN TRIAL OR GET OUT
        final int duration = inTrial ? 3 : 35;
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(duration));
        if (!inTrial) {
            busHandler.pushExpectedEvent(NextEvent.PHASE);
            busHandler.pushExpectedEvent(NextEvent.PHASE);
            busHandler.pushExpectedEvent(NextEvent.PHASE);
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        }
        clock.addDeltaFromReality(it.toDurationMillis());
        if (!inTrial) {
            assertTrue(busHandler.isCompleted(DELAY));
        }
        final boolean ifRepair = false;
        if (ifRepair) {
            BundleTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
            sortEventsOnBundle(bundleRepair);

            // Quick check
            SubscriptionTimeline bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
            assertEquals(bpRepair.getExistingEvents().size(), 2);

            final SubscriptionTimeline aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
            assertEquals(aoRepair.getExistingEvents().size(), 2);

            final SubscriptionTimeline aoRepair2 = getSubscriptionRepair(aoSubscription2.getId(), bundleRepair);
            assertEquals(aoRepair2.getExistingEvents().size(), 2);

            final DateTime bpChangeDate = clock.getUTCNow().minusDays(1);

            final List<DeletedEvent> des = new LinkedList<SubscriptionTimeline.DeletedEvent>();
            des.add(createDeletedEvent(bpRepair.getExistingEvents().get(1).getEventId()));

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
            final NewEvent ne = createNewEvent(SubscriptionTransitionType.CHANGE, bpChangeDate, spec);

            bpRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));

            bundleRepair = createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));

            // TIME TO  REPAIR
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            busHandler.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
            repairApi.repairBundle(bundleRepair, false, callContext);
            assertTrue(busHandler.isCompleted(DELAY));

            final SubscriptionData newAoSubscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(aoSubscription.getId(), callContext));
            assertEquals(newAoSubscription.getState(), SubscriptionState.CANCELLED);
            assertEquals(newAoSubscription.getAllTransitions().size(), 2);
            assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

            final SubscriptionData newAoSubscription2 = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(aoSubscription2.getId(), callContext));
            assertEquals(newAoSubscription2.getState(), SubscriptionState.ACTIVE);
            assertEquals(newAoSubscription2.getAllTransitions().size(), 2);
            assertEquals(newAoSubscription2.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);


            final SubscriptionData newBaseSubscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(baseSubscription.getId(), callContext));
            assertEquals(newBaseSubscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
            assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

            assertListenerStatus();
        }
    }

    protected SubscriptionTimeline createSubscriptionReapir(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
        return new SubscriptionTimeline() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }

            @Override
            public List<NewEvent> getNewEvents() {
                return newEvents;
            }

            @Override
            public List<ExistingEvent> getExistingEvents() {
                return null;
            }

            @Override
            public List<DeletedEvent> getDeletedEvents() {
                return deletedEvents;
            }

            @Override
            public long getActiveVersion() {
                return 0;
            }
        };
    }


    protected BundleTimeline createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionTimeline> subscriptionRepair) {
        return new BundleTimeline() {
            @Override
            public String getViewId() {
                return viewId;
            }

            @Override
            public List<SubscriptionTimeline> getSubscriptions() {
                return subscriptionRepair;
            }

            @Override
            public UUID getId() {
                return bundleId;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }

            @Override
            public String getExternalKey() {
                return null;
            }
        };
    }

    protected ExistingEvent createExistingEventForAssertion(final SubscriptionTransitionType type,
                                                            final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
                                                            final DateTime effectiveDateTime) {

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        final ExistingEvent ev = new ExistingEvent() {
            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return type;
            }

            @Override
            public DateTime getRequestedDate() {
                return null;
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return spec;
            }

            @Override
            public UUID getEventId() {
                return null;
            }

            @Override
            public DateTime getEffectiveDate() {
                return effectiveDateTime;
            }

            @Override
            public String getPlanPhaseName() {
                return null;
            }
        };
        return ev;
    }

    protected SubscriptionTimeline getSubscriptionRepair(final UUID id, final BundleTimeline bundleRepair) {
        for (final SubscriptionTimeline cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionReapir " + id);
        return null;
    }

    protected void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {

        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName()));
        assertEquals(input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType()));
        assertEquals(input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory()));
        assertEquals(input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName()));
        assertEquals(input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName());
        log.info(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod()));
        assertEquals(input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod());
        log.info(String.format("Got %s -> Expected %s", input.getEffectiveDate(), expected.getEffectiveDate()));
        assertEquals(input.getEffectiveDate(), expected.getEffectiveDate());
    }

    protected DeletedEvent createDeletedEvent(final UUID eventId) {
        return new DeletedEvent() {
            @Override
            public UUID getEventId() {
                return eventId;
            }
        };
    }

    protected NewEvent createNewEvent(final SubscriptionTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {

        return new NewEvent() {
            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return type;
            }

            @Override
            public DateTime getRequestedDate() {
                return requestedDate;
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return spec;
            }
        };
    }

    protected void sortEventsOnBundle(final BundleTimeline bundle) {
        if (bundle.getSubscriptions() == null) {
            return;
        }
        for (final SubscriptionTimeline cur : bundle.getSubscriptions()) {
            if (cur.getExistingEvents() != null) {
                sortExistingEvent(cur.getExistingEvents());
            }
            if (cur.getNewEvents() != null) {
                sortNewEvent(cur.getNewEvents());
            }
        }
    }

    protected void sortExistingEvent(final List<ExistingEvent> events) {
        Collections.sort(events, new Comparator<ExistingEvent>() {
            @Override
            public int compare(final ExistingEvent arg0, final ExistingEvent arg1) {
                return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
            }
        });
    }

    protected void sortNewEvent(final List<NewEvent> events) {
        Collections.sort(events, new Comparator<NewEvent>() {
            @Override
            public int compare(final NewEvent arg0, final NewEvent arg1) {
                return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
            }
        });
    }
}
