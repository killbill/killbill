/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOnsSpecifier;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionHelper {

    private final Logger log = LoggerFactory.getLogger(TestSubscriptionHelper.class);

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final Clock clock;
    private final MockNonEntityDao mockNonEntityDao;
    private final InternalCallContext internalCallContext;
    private final TestApiListener testListener;
    private final SubscriptionDao dao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public TestSubscriptionHelper(final SubscriptionBaseInternalApi subscriptionApi,
                                  final Clock clock,
                                  final MockNonEntityDao mockNonEntityDao,
                                  final InternalCallContext internalCallContext,
                                  final TestApiListener testListener,
                                  final SubscriptionDao dao,
                                  final InternalCallContextFactory internalCallContextFactory) {
        this.subscriptionApi = subscriptionApi;
        this.clock = clock;
        this.mockNonEntityDao = mockNonEntityDao;
        this.internalCallContext = internalCallContext;
        this.testListener = testListener;
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public DryRunArguments createDryRunArguments(final UUID subscriptionId, final UUID bundleId, final EntitlementSpecifier spec, final LocalDate requestedDate, final SubscriptionEventType type, final BillingActionPolicy billingActionPolicy) {
        return new DryRunArguments() {
            @Override
            public DryRunType getDryRunType() {
                return DryRunType.SUBSCRIPTION_ACTION;
            }

            @Override
            public EntitlementSpecifier getEntitlementSpecifier() {
                return spec;
            }

            @Override
            public SubscriptionEventType getAction() {
                return type;
            }

            @Override
            public UUID getSubscriptionId() {
                return subscriptionId;
            }

            @Override
            public LocalDate getEffectiveDate() {
                return requestedDate;
            }

            @Override
            public UUID getBundleId() {
                return bundleId;
            }

            @Override
            public BillingActionPolicy getBillingActionPolicy() {
                return billingActionPolicy;
            }

        };
    }

    public DefaultSubscriptionBase createSubscription(final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet, final LocalDate requestedDate)
            throws SubscriptionBaseApiException {
        return createSubscription(bundle, productName, term, planSet, null, requestedDate);
    }

    public DefaultSubscriptionBase createSubscription(final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet)
            throws SubscriptionBaseApiException {
        return createSubscription(bundle, productName, term, planSet, null, null);
    }

    public DefaultSubscriptionBase createSubscription(final boolean noEvents, final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet) throws SubscriptionBaseApiException {
        return createSubscription(noEvents, bundle, productName, term, planSet, null, null);
    }

    public DefaultSubscriptionBase createSubscription(final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet, final PhaseType phaseType, final LocalDate requestedDate)
            throws SubscriptionBaseApiException {
        return createSubscription(false, bundle, productName, term, planSet, phaseType, requestedDate);
    }

    private DefaultSubscriptionBase createSubscription(final boolean noEvents, @Nullable final SubscriptionBaseBundle bundle, final String productName, final BillingPeriod term, final String planSet, final PhaseType phaseType, final LocalDate requestedDate)
            throws SubscriptionBaseApiException {
        // Make sure the right account information is used
        final InternalCallContext internalCallContext = bundle == null ? this.internalCallContext : internalCallContextFactory.createInternalCallContext(bundle.getAccountId(),
                                                                                                                                                         ObjectType.ACCOUNT,
                                                                                                                                                         this.internalCallContext.getUpdatedBy(),
                                                                                                                                                         this.internalCallContext.getCallOrigin(),
                                                                                                                                                         this.internalCallContext.getContextUserType(),
                                                                                                                                                         this.internalCallContext.getUserToken(),
                                                                                                                                                         this.internalCallContext.getTenantRecordId());

        boolean bundleExists = false;
        if (bundle != null) {
            try {
                bundleExists = (subscriptionApi.getBundleFromId(bundle.getId(), internalCallContext) != null);
            } catch (final SubscriptionBaseApiException ignored) {
            }
        }

        if (!noEvents && (requestedDate == null || requestedDate.compareTo(clock.getUTCToday()) <= 0)) {
            testListener.pushExpectedEvent(NextEvent.CREATE);
        }

        final ImmutableList<EntitlementSpecifier> entitlementSpecifiers = ImmutableList.<EntitlementSpecifier>of(new EntitlementSpecifier() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return new PlanPhaseSpecifier(productName, term, planSet, phaseType);
            }

            @Override
            public Integer getBillCycleDay() {
                return null;
            }

            @Override
            public List<PlanPhasePriceOverride> getOverrides() {
                return null;
            }
        });
        final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier = new SubscriptionBaseWithAddOnsSpecifier(bundle == null ||!bundleExists ? null : bundle.getId(),
                                                                                                                                bundle == null ? null : bundle.getExternalKey(),
                                                                                                                                entitlementSpecifiers,
                                                                                                                                requestedDate,
                                                                                                                                false);
        final SubscriptionBaseWithAddOns subscriptionBaseWithAddOns = subscriptionApi.createBaseSubscriptionsWithAddOns(ImmutableList.<SubscriptionBaseWithAddOnsSpecifier>of(subscriptionBaseWithAddOnsSpecifier),
                                                                                                                        false,
                                                                                                                        internalCallContext).get(0);
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionBaseWithAddOns.getSubscriptionBaseList().get(0);
        assertNotNull(subscription);

        testListener.assertListenerStatus();

        mockNonEntityDao.addTenantRecordIdMapping(subscription.getId(), internalCallContext);
        mockNonEntityDao.addAccountRecordIdMapping(subscription.getId(), internalCallContext);

        mockNonEntityDao.addTenantRecordIdMapping(subscription.getBundleId(), internalCallContext);
        mockNonEntityDao.addAccountRecordIdMapping(subscription.getBundleId(), internalCallContext);

        return subscription;
    }

    public void checkNextPhaseChange(final DefaultSubscriptionBase subscription, final int expPendingEvents, final DateTime expPhaseChange) {
        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
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
                    assertEquals(ApiEventType.CHANGE, uEvent.getApiEventType());
                    assertEquals(foundChange, false);
                    foundChange = true;
                } else {
                    assertFalse(true);
                }
            }
        }
    }

    public void assertDateWithin(final DateTime in, final DateTime lower, final DateTime upper) {
        assertTrue(in.isEqual(lower) || in.isAfter(lower), "in=" + in + ", lower=" + lower);
        assertTrue(in.isEqual(upper) || in.isBefore(upper), "in=" + in + ", upper=" + upper);
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
                return null;
            }
            @Override
            public LocalDate addToLocalDate(final LocalDate localDate) {
                return null;
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
        return new PlanPhaseSpecifier(productName, term, priceList, phaseType);
    }

    public void printEvents(final List<SubscriptionBaseEvent> events) {
        for (final SubscriptionBaseEvent cur : events) {
            log.debug("Inspect event " + cur);
        }
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

    public static DateTime addDuration(final DateTime input, final Duration duration) {
        final List<Duration> list = new ArrayList<Duration>();
        list.add(duration);
        return addOrRemoveDuration(input, list, true);
    }
}
