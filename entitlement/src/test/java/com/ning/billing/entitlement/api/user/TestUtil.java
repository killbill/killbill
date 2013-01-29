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

package com.ning.billing.entitlement.api.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.ErrorCode;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestUtil {

    private final Logger log = LoggerFactory.getLogger(TestUtil.class);

    private final EntitlementUserApi entitlementApi;

    private final Clock clock;

    private final InternalCallContext callContext;

    private final TestApiListener testListener;

    private final EntitlementDao dao;



    @Inject
    public TestUtil(final EntitlementUserApi entitlementApi, final Clock clock, final InternalCallContext callContext, final TestApiListener testListener, final EntitlementDao dao) {
        this.entitlementApi = entitlementApi;
        this.clock = clock;
        this.callContext = callContext;
        this.testListener = testListener;
        this.dao = dao;
    }


    public SubscriptionData createSubscription(final SubscriptionBundle bundle, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
            throws EntitlementUserApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, requestedDate);
    }

    public SubscriptionData createSubscription(final SubscriptionBundle bundle, final String productName, final BillingPeriod term, final String planSet)
            throws EntitlementUserApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, null);
    }

    public SubscriptionData createSubscriptionWithBundle(final UUID bundleId, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
            throws EntitlementUserApiException {
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundleId,
                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSet, null),
                                                                                                   requestedDate == null ? clock.getUTCNow() : requestedDate, callContext.toCallContext());
        assertNotNull(subscription);

        assertTrue(testListener.isCompleted(5000));
        return subscription;
    }

    public void checkNextPhaseChange(final SubscriptionData subscription, final int expPendingEvents, final DateTime expPhaseChange) {
        final List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), callContext);
        assertNotNull(events);
        printEvents(events);
        assertEquals(events.size(), expPendingEvents);
        if (events.size() > 0 && expPhaseChange != null) {
            boolean foundPhase = false;
            boolean foundChange = false;

            for (final EntitlementEvent cur : events) {
                if (cur instanceof PhaseEvent) {
                    assertEquals(foundPhase, false);
                    foundPhase = true;
                    assertEquals(cur.getEffectiveDate(), expPhaseChange);
                } else if (cur instanceof ApiEvent) {
                    final ApiEvent uEvent = (ApiEvent) cur;
                    assertEquals(ApiEventType.CHANGE, uEvent.getEventType());
                    assertEquals(foundChange, false);
                    foundChange = true;
                } else {
                    assertFalse(true);
                }
            }
        }
    }

    public void assertDateWithin(final DateTime in, final DateTime lower, final DateTime upper) {
        assertTrue(in.isEqual(lower) || in.isAfter(lower));
        assertTrue(in.isEqual(upper) || in.isBefore(upper));
    }

    public Duration getDurationDay(final int days) {
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.DAYS;
            }

            @Override
            public int getNumber() {
                return days;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
                return null;
            }

            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
            }
        };
        return result;
    }

    public Duration getDurationMonth(final int months) {
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.MONTHS;
            }

            @Override
            public int getNumber() {
                return months;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
            }
        };
        return result;
    }

    public Duration getDurationYear(final int years) {
        final Duration result = new Duration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.YEARS;
            }

            @Override
            public int getNumber() {
                return years;
            }

            @Override
            public DateTime addToDateTime(final DateTime dateTime) {
                return dateTime.plusYears(years);
            }

            @Override
            public Period toJodaPeriod() {
                throw new UnsupportedOperationException();
            }
        };
        return result;
    }

    public PlanPhaseSpecifier getProductSpecifier(final String productName, final String priceList,
                                                  final BillingPeriod term,
                                                  @Nullable final PhaseType phaseType) {
        return new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, priceList, phaseType);
    }

    public void printEvents(final List<EntitlementEvent> events) {
        for (final EntitlementEvent cur : events) {
            log.debug("Inspect event " + cur);
        }
    }

    public void printSubscriptionTransitions(final List<EffectiveSubscriptionInternalEvent> transitions) {
        for (final EffectiveSubscriptionInternalEvent cur : transitions) {
            log.debug("Transition " + cur);
        }
    }

    /**
     * ***********************************************************
     * Utilities for migration tests
     * *************************************************************
     */

    public EntitlementAccountMigration createAccountForMigrationTest(final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> cases) {
        return new EntitlementAccountMigration() {
            private final UUID accountId = UUID.randomUUID();

            @Override
            public EntitlementBundleMigration[] getBundles() {
                final List<EntitlementBundleMigration> bundles = new ArrayList<EntitlementBundleMigration>();
                final EntitlementBundleMigration bundle0 = new EntitlementBundleMigration() {
                    @Override
                    public EntitlementSubscriptionMigration[] getSubscriptions() {
                        final EntitlementSubscriptionMigration[] result = new EntitlementSubscriptionMigration[cases.size()];

                        for (int i = 0; i < cases.size(); i++) {
                            final List<EntitlementSubscriptionMigrationCaseWithCTD> curCases = cases.get(i);
                            final EntitlementSubscriptionMigration subscription = new EntitlementSubscriptionMigration() {
                                @Override
                                public EntitlementSubscriptionMigrationCaseWithCTD[] getSubscriptionCases() {
                                    return curCases.toArray(new EntitlementSubscriptionMigrationCaseWithCTD[curCases.size()]);
                                }

                                @Override
                                public ProductCategory getCategory() {
                                    return curCases.get(0).getPlanPhaseSpecifier().getProductCategory();
                                }

                                @Override
                                public DateTime getChargedThroughDate() {
                                    for (final EntitlementSubscriptionMigrationCaseWithCTD cur : curCases) {
                                        if (cur.getChargedThroughDate() != null) {
                                            return cur.getChargedThroughDate();
                                        }
                                    }
                                    return null;
                                }
                            };
                            result[i] = subscription;
                        }
                        return result;
                    }

                    @Override
                    public String getBundleKey() {
                        return "12345";
                    }
                };
                bundles.add(bundle0);
                return bundles.toArray(new EntitlementBundleMigration[bundles.size()]);
            }

            @Override
            public UUID getAccountKey() {
                return accountId;
            }
        };
    }

    public EntitlementAccountMigration createAccountForMigrationWithRegularBasePlanAndAddons(final DateTime initialBPstart, final DateTime initalAddonStart) {

        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initialBPstart,
                null,
                initialBPstart.plusYears(1)));

        final List<EntitlementSubscriptionMigrationCaseWithCTD> firstAddOnCases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT),
                initalAddonStart,
                initalAddonStart.plusMonths(1),
                initalAddonStart.plusMonths(1)));
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initalAddonStart.plusMonths(1),
                null,
                null));

        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        input.add(firstAddOnCases);
        return createAccountForMigrationTest(input);
    }

    public EntitlementAccountMigration createAccountForMigrationWithRegularBasePlan(final DateTime startDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                null,
                startDate.plusYears(1)));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public EntitlementAccountMigration createAccountForMigrationWithRegularBasePlanFutreCancelled(final DateTime startDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                startDate.plusYears(1),
                startDate.plusYears(1)));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public EntitlementAccountMigration createAccountForMigrationFuturePendingPhase(final DateTime trialDate) {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL),
                trialDate,
                trialDate.plusDays(30),
                trialDate.plusDays(30)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                trialDate.plusDays(30),
                null,
                null));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public EntitlementAccountMigration createAccountForMigrationFuturePendingChange() {
        final List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(10);
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate,
                effectiveDate.plusMonths(1),
                effectiveDate.plusMonths(1)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate.plusMonths(1).plusDays(1),
                null,
                null));
        final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }


    public SubscriptionTimeline createSubscriptionRepair(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
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
                return 1;
            }
        };
    }

    public BundleTimeline createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionTimeline> subscriptionRepair) {
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

    public ExistingEvent createExistingEventForAssertion(final SubscriptionTransitionType type,
                                                            final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
                                                            final DateTime effectiveDateTime) {
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        return new ExistingEvent() {
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
    }

    public SubscriptionTimeline getSubscriptionRepair(final UUID id, final BundleTimeline bundleRepair) {
        for (final SubscriptionTimeline cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionRepair " + id);
        return null;
    }

    public void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {
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

    public DeletedEvent createDeletedEvent(final UUID eventId) {
        return new DeletedEvent() {
            @Override
            public UUID getEventId() {
                return eventId;
            }
        };
    }

    public NewEvent createNewEvent(final SubscriptionTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {
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

    public void sortEventsOnBundle(final BundleTimeline bundle) {
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

    public void sortExistingEvent(final List<ExistingEvent> events) {
        Collections.sort(events, new Comparator<ExistingEvent>() {
            @Override
            public int compare(final ExistingEvent arg0, final ExistingEvent arg1) {
                return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
            }
        });
    }

    public void sortNewEvent(final List<NewEvent> events) {
        Collections.sort(events, new Comparator<NewEvent>() {
            @Override
            public int compare(final NewEvent arg0, final NewEvent arg1) {
                return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
            }
        });
    }





    public static class EntitlementSubscriptionMigrationCaseWithCTD implements EntitlementSubscriptionMigrationCase {

        private final PlanPhaseSpecifier pps;
        private final DateTime effDt;
        private final DateTime cancelDt;
        private final DateTime ctd;

        public EntitlementSubscriptionMigrationCaseWithCTD(final PlanPhaseSpecifier pps, final DateTime effDt, final DateTime cancelDt, final DateTime ctd) {
            this.pps = pps;
            this.cancelDt = cancelDt;
            this.effDt = effDt;
            this.ctd = ctd;
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
            return pps;
        }

        @Override
        public DateTime getEffectiveDate() {
            return effDt;
        }

        @Override
        public DateTime getCancelledDate() {
            return cancelDt;
        }

        public DateTime getChargedThroughDate() {
            return ctd;
        }
    }


    public interface TestWithExceptionCallback {

        public void doTest() throws EntitlementRepairException, EntitlementUserApiException;
    }

    public static class TestWithException {

        public void withException(final TestWithExceptionCallback callback, final ErrorCode code) throws Exception {
            try {
                callback.doTest();
                Assert.fail("Failed to catch exception " + code);
            } catch (EntitlementRepairException e) {
                assertEquals(e.getCode(), code.getCode());
            }
        }
    }
}
