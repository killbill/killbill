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

package com.ning.billing.subscription.api.user;

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
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.clock.Clock;
import com.ning.billing.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi.AccountMigration;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi.BundleMigration;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi.SubscriptionMigration;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi.SubscriptionMigrationCase;
import com.ning.billing.subscription.api.timeline.BundleBaseTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseRepairException;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline.DeletedEvent;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline.ExistingEvent;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline.NewEvent;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.events.SubscriptionBaseEvent;
import com.ning.billing.subscription.events.phase.PhaseEvent;
import com.ning.billing.subscription.events.user.ApiEvent;
import com.ning.billing.subscription.events.user.ApiEventType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionHelper {

    private final Logger log = LoggerFactory.getLogger(TestSubscriptionHelper.class);

    private final SubscriptionBaseInternalApi subscriptionApi;

    private final Clock clock;

    private final InternalCallContext callContext;

    private final TestApiListener testListener;

    private final SubscriptionDao dao;

    @Inject
    public TestSubscriptionHelper(final SubscriptionBaseInternalApi subscriptionApi, final Clock clock, final InternalCallContext callContext, final TestApiListener testListener, final SubscriptionDao dao) {
        this.subscriptionApi = subscriptionApi;
        this.clock = clock;
        this.callContext = callContext;
        this.testListener = testListener;
        this.dao = dao;
    }

    public DefaultSubscriptionBase createSubscription(final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
            throws SubscriptionBaseApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, requestedDate);
    }

    public DefaultSubscriptionBase createSubscription(final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet)
            throws SubscriptionBaseApiException {
        return createSubscriptionWithBundle(bundle.getId(), productName, term, planSet, null);
    }

    public DefaultSubscriptionBase createSubscriptionWithBundle(final UUID bundleId, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate)
            throws SubscriptionBaseApiException {
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionApi.createSubscription(bundleId,
                                                                                                                  new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSet, null),
                                                                                                                  requestedDate == null ? clock.getUTCNow() : requestedDate, callContext);
        assertNotNull(subscription);

        testListener.assertListenerStatus();

        return subscription;
    }

    public void checkNextPhaseChange(final DefaultSubscriptionBase subscription, final int expPendingEvents, final DateTime expPhaseChange) {
        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), callContext);
        assertNotNull(events);
        printEvents(events);
        assertEquals(events.size(), expPendingEvents);
        if (events.size() > 0 && expPhaseChange != null) {
            boolean foundPhase = false;
            boolean foundChange = false;

            for (final SubscriptionBaseEvent cur : events) {
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

    public void printEvents(final List<SubscriptionBaseEvent> events) {
        for (final SubscriptionBaseEvent cur : events) {
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

    public AccountMigration createAccountForMigrationTest(final List<List<SubscriptionMigrationCaseWithCTD>> cases) {
        return new AccountMigration() {
            private final UUID accountId = UUID.randomUUID();

            @Override
            public BundleMigration[] getBundles() {
                final List<BundleMigration> bundles = new ArrayList<BundleMigration>();
                final BundleMigration bundle0 = new BundleMigration() {
                    @Override
                    public SubscriptionMigration[] getSubscriptions() {
                        final SubscriptionMigration[] result = new SubscriptionMigration[cases.size()];

                        for (int i = 0; i < cases.size(); i++) {
                            final List<SubscriptionMigrationCaseWithCTD> curCases = cases.get(i);
                            final SubscriptionMigration subscription = new SubscriptionMigration() {
                                @Override
                                public SubscriptionMigrationCaseWithCTD[] getSubscriptionCases() {
                                    return curCases.toArray(new SubscriptionMigrationCaseWithCTD[curCases.size()]);
                                }

                                @Override
                                public ProductCategory getCategory() {
                                    return curCases.get(0).getPlanPhaseSpecifier().getProductCategory();
                                }

                                @Override
                                public DateTime getChargedThroughDate() {
                                    for (final SubscriptionMigrationCaseWithCTD cur : curCases) {
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
                return bundles.toArray(new BundleMigration[bundles.size()]);
            }

            @Override
            public UUID getAccountKey() {
                return accountId;
            }
        };
    }

    public AccountMigration createAccountForMigrationWithRegularBasePlanAndAddons(final DateTime initialBPstart, final DateTime initalAddonStart) {

        final List<SubscriptionMigrationCaseWithCTD> cases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initialBPstart,
                null,
                initialBPstart.plusYears(1)));

        final List<SubscriptionMigrationCaseWithCTD> firstAddOnCases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        firstAddOnCases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT),
                initalAddonStart,
                initalAddonStart.plusMonths(1),
                initalAddonStart.plusMonths(1)));
        firstAddOnCases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initalAddonStart.plusMonths(1),
                null,
                null));

        final List<List<SubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<SubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        input.add(firstAddOnCases);
        return createAccountForMigrationTest(input);
    }

    public AccountMigration createAccountForMigrationWithRegularBasePlan(final DateTime startDate) {
        final List<SubscriptionMigrationCaseWithCTD> cases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                null,
                startDate.plusYears(1)));
        final List<List<SubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<SubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public AccountMigration createAccountForMigrationWithRegularBasePlanFutreCancelled(final DateTime startDate) {
        final List<SubscriptionMigrationCaseWithCTD> cases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                startDate.plusYears(1),
                startDate.plusYears(1)));
        final List<List<SubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<SubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public AccountMigration createAccountForMigrationFuturePendingPhase(final DateTime trialDate) {
        final List<SubscriptionMigrationCaseWithCTD> cases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL),
                trialDate,
                trialDate.plusDays(30),
                trialDate.plusDays(30)));
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                trialDate.plusDays(30),
                null,
                null));
        final List<List<SubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<SubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public AccountMigration createAccountForMigrationFuturePendingChange() {
        final List<SubscriptionMigrationCaseWithCTD> cases = new LinkedList<SubscriptionMigrationCaseWithCTD>();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(10);
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate,
                effectiveDate.plusMonths(1),
                effectiveDate.plusMonths(1)));
        cases.add(new SubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate.plusMonths(1).plusDays(1),
                null,
                null));
        final List<List<SubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<SubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountForMigrationTest(input);
    }

    public SubscriptionBaseTimeline createSubscriptionRepair(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
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
                return 1;
            }
        };
    }

    public BundleBaseTimeline createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionBaseTimeline> subscriptionRepair) {
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

    public ExistingEvent createExistingEventForAssertion(final SubscriptionBaseTransitionType type,
                                                         final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
                                                         final DateTime effectiveDateTime) {
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        return new ExistingEvent() {
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
    }

    public SubscriptionBaseTimeline getSubscriptionRepair(final UUID id, final BundleBaseTimeline bundleRepair) {
        for (final SubscriptionBaseTimeline cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionRepair " + id);
        return null;
    }

    public void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {
        log.debug(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName()));
        assertEquals(input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName());
        log.debug(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType()));
        assertEquals(input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType());
        log.debug(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory()));
        assertEquals(input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory());
        log.debug(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName()));
        assertEquals(input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName());
        log.debug(String.format("Got %s -> Expected %s", input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod()));
        assertEquals(input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod());
        log.debug(String.format("Got %s -> Expected %s", input.getEffectiveDate(), expected.getEffectiveDate()));
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

    public NewEvent createNewEvent(final SubscriptionBaseTransitionType type, final DateTime requestedDate, final PlanPhaseSpecifier spec) {
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

    public void sortEventsOnBundle(final BundleBaseTimeline bundle) {
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

    public static DateTime addOrRemoveDuration(final DateTime input, final List<Duration> durations, final boolean add) {
        DateTime result = input;
        for (final Duration cur : durations) {
            switch (cur.getUnit()) {
                case DAYS:
                    result = add ? result.plusDays(cur.getNumber()) : result.minusDays(cur.getNumber());
                    break;

                case MONTHS:
                    result = add ? result.plusMonths(cur.getNumber()) : result.minusMonths(cur.getNumber());
                    break;

                case YEARS:
                    result = add ? result.plusYears(cur.getNumber()) : result.minusYears(cur.getNumber());
                    break;
                case UNLIMITED:
                default:
                    throw new RuntimeException("Trying to move to unlimited time period");
            }
        }
        return result;
    }

    public static DateTime addDuration(final DateTime input, final List<Duration> durations) {
        return addOrRemoveDuration(input, durations, true);
    }

    public static DateTime removeDuration(final DateTime input, final List<Duration> durations) {
        return addOrRemoveDuration(input, durations, false);
    }

    public static DateTime addDuration(final DateTime input, final Duration duration) {
        final List<Duration> list = new ArrayList<Duration>();
        list.add(duration);
        return addOrRemoveDuration(input, list, true);
    }

    public static DateTime removeDuration(final DateTime input, final Duration duration) {
        final List<Duration> list = new ArrayList<Duration>();
        list.add(duration);
        return addOrRemoveDuration(input, list, false);
    }

    public static class SubscriptionMigrationCaseWithCTD implements SubscriptionMigrationCase {

        private final PlanPhaseSpecifier pps;
        private final DateTime effDt;
        private final DateTime cancelDt;
        private final DateTime ctd;

        public SubscriptionMigrationCaseWithCTD(final PlanPhaseSpecifier pps, final DateTime effDt, final DateTime cancelDt, final DateTime ctd) {
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

        public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException;
    }

    public static class TestWithException {

        public void withException(final TestWithExceptionCallback callback, final ErrorCode code) throws Exception {
            try {
                callback.doTest();
                Assert.fail("Failed to catch exception " + code);
            } catch (SubscriptionBaseRepairException e) {
                assertEquals(e.getCode(), code.getCode());
            }
        }
    }
}
