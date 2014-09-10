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

package org.killbill.billing.beatrix.integration;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.timeline.BundleBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.DeletedEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.ExistingEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.NewEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionEvents;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestRepairIntegration extends TestIntegrationBase {

    @Test(groups = "slow", enabled = false)
    public void testRepairChangeBPWithAddonIncludedIntrial() throws Exception {
        log.info("Starting testRepairChangeBPWithAddonIncludedIntrial");
        testRepairChangeBPWithAddonIncluded(true);
    }

    @Test(groups = "slow", enabled = false)
    public void testRepairChangeBPWithAddonIncludedOutOfTrial() throws Exception {
        log.info("Starting testRepairChangeBPWithAddonIncludedOutOfTrial");
        testRepairChangeBPWithAddonIncluded(false);
    }

    private void testRepairChangeBPWithAddonIncluded(final boolean inTrial) throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        clock.addDeltaFromReality(it.toDurationMillis());

        final DefaultEntitlement aoEntitlement1 = addAOEntitlementAndCheckForCompletion(bpEntitlement.getBundleId(), "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT);
        final DefaultEntitlement aoEntitlement2 = addAOEntitlementAndCheckForCompletion(bpEntitlement.getBundleId(), "Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT);

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
            assertListenerStatus();
        }
        final boolean ifRepair = false;
        if (ifRepair) {
            BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bpEntitlement.getSubscriptionBase().getBundleId(), callContext);
            sortEventsOnBundle(bundleRepair);

            // Quick check
            SubscriptionBaseTimeline bpRepair = getSubscriptionRepair(bpEntitlement.getId(), bundleRepair);
            assertEquals(bpRepair.getExistingEvents().size(), 2);

            final SubscriptionBaseTimeline aoRepair = getSubscriptionRepair(aoEntitlement1.getId(), bundleRepair);
            assertEquals(aoRepair.getExistingEvents().size(), 2);

            final SubscriptionBaseTimeline aoRepair2 = getSubscriptionRepair(aoEntitlement2.getId(), bundleRepair);
            assertEquals(aoRepair2.getExistingEvents().size(), 2);

            final DateTime bpChangeDate = clock.getUTCNow().minusDays(1);

            final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
            des.add(createDeletedEvent(bpRepair.getExistingEvents().get(1).getEventId()));

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
            final NewEvent ne = createNewEvent(SubscriptionBaseTransitionType.CHANGE, bpChangeDate, spec);

            bpRepair = createSubscriptionReapir(bpEntitlement.getId(), des, Collections.singletonList(ne));

            bundleRepair = createBundleRepair(bpEntitlement.getSubscriptionBase().getBundleId(), bundleRepair.getViewId(), Collections.singletonList(bpRepair));

            // TIME TO  REPAIR
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            busHandler.pushExpectedEvent(NextEvent.REPAIR_BUNDLE);
            repairApi.repairBundle(bundleRepair, false, callContext);
            assertListenerStatus();

            final DefaultSubscriptionBase newAoSubscription = (DefaultSubscriptionBase) aoEntitlement1.getSubscriptionBase();
            assertEquals(newAoSubscription.getState(), EntitlementState.CANCELLED);
            assertEquals(newAoSubscription.getAllTransitions().size(), 2);
            assertEquals(newAoSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

            final DefaultSubscriptionBase newAoSubscription2 = (DefaultSubscriptionBase) aoEntitlement2.getSubscriptionBase();
            assertEquals(newAoSubscription2.getState(), EntitlementState.ACTIVE);
            assertEquals(newAoSubscription2.getAllTransitions().size(), 2);
            assertEquals(newAoSubscription2.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

            final DefaultSubscriptionBase newBaseSubscription = (DefaultSubscriptionBase) bpEntitlement.getSubscriptionBase();
            assertEquals(newBaseSubscription.getState(), EntitlementState.ACTIVE);
            assertEquals(newBaseSubscription.getAllTransitions().size(), 3);
            assertEquals(newBaseSubscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION + 1);

            assertListenerStatus();
        }

        checkNoMoreInvoiceToGenerate(account);
    }

    protected SubscriptionBaseTimeline createSubscriptionReapir(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
        return new SubscriptionBaseTimeline() {
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

    protected BundleBaseTimeline createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionBaseTimeline> subscriptionRepair) {
        return new BundleBaseTimeline() {
            @Override
            public String getViewId() {
                return viewId;
            }

            @Override
            public List<SubscriptionBaseTimeline> getSubscriptions() {
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

    protected ExistingEvent createExistingEventForAssertion(final SubscriptionBaseTransitionType type,
                                                            final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
                                                            final DateTime effectiveDateTime) {

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        final ExistingEvent ev = new ExistingEvent() {
            @Override
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
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

    protected SubscriptionBaseTimeline getSubscriptionRepair(final UUID id, final BundleBaseTimeline bundleRepair) {
        for (final SubscriptionBaseTimeline cur : bundleRepair.getSubscriptions()) {
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

    protected NewEvent createNewEvent(final SubscriptionBaseTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {

        return new NewEvent() {
            @Override
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
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

    protected void sortEventsOnBundle(final BundleBaseTimeline bundle) {
        if (bundle.getSubscriptions() == null) {
            return;
        }
        for (final SubscriptionBaseTimeline cur : bundle.getSubscriptions()) {
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
