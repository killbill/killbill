/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionHelper {

    private final Logger log = LoggerFactory.getLogger(TestSubscriptionHelper.class);

    private final AccountUserApi accountApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final Clock clock;
    private final InternalCallContext internalCallContext;
    private final CallContext callContext;
    private final TestApiListener testListener;
    private final SubscriptionDao dao;

    @Inject
    public TestSubscriptionHelper(final AccountUserApi accountApi, final SubscriptionBaseInternalApi subscriptionApi, final Clock clock, final InternalCallContext internallCallContext, final CallContext callContext, final TestApiListener testListener, final SubscriptionDao dao) {
        this.accountApi = accountApi;
        this.subscriptionApi = subscriptionApi;
        this.clock = clock;
        this.internalCallContext = internallCallContext;
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
                                                                                                                  new PlanPhaseSpecifier(productName, term, planSet, null), null,
                                                                                                                  requestedDate == null ? clock.getUTCNow() : requestedDate, false, internalCallContext);
        assertNotNull(subscription);

        testListener.assertListenerStatus();

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
        assertTrue(in.isEqual(lower) || in.isAfter(lower));
        assertTrue(in.isEqual(upper) || in.isBefore(upper));
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
