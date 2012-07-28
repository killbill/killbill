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

package com.ning.billing.entitlement.api.timeline;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;

import static org.testng.Assert.assertEquals;

public abstract class TestApiBaseRepair extends TestApiBase {
    protected static final Logger log = LoggerFactory.getLogger(TestApiBaseRepair.class);

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

    protected SubscriptionTimeline createSubscriptionRepair(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
        return new SubscriptionTimeline() {
            @Override
            public UUID getId() {
                return id;
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
            public UUID getBundleId() {
                return bundleId;
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

    protected SubscriptionTimeline getSubscriptionRepair(final UUID id, final BundleTimeline bundleRepair) {
        for (final SubscriptionTimeline cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionRepair " + id);
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
