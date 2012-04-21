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
package com.ning.billing.entitlement.api.repair;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.DeletedEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;


public abstract class TestApiBaseRepair extends TestApiBase {


    public interface TestWithExceptionCallback {
        public void doTest() throws EntitlementRepairException, EntitlementUserApiException;
    }
    
    public static class TestWithException {
        public void withException(TestWithExceptionCallback callback, ErrorCode code) throws Exception {
            try {
                callback.doTest();
                Assert.fail("Failed to catch exception " + code);
            } catch (EntitlementRepairException e) {
                assertEquals(e.getCode(), code.getCode());
            }
        }
    }

    
    protected SubscriptionRepair createSubscriptionReapir(final UUID id, final List<DeletedEvent> deletedEvents, final List<NewEvent> newEvents) {
        return new SubscriptionRepair() {
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
        };
    }

    protected BundleRepair createBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionRepair> subscriptionRepair) {
        return new BundleRepair() {
            @Override
            public String getViewId() {
                return viewId;
            }
            @Override
            public List<SubscriptionRepair> getSubscriptions() {
                return subscriptionRepair;
            }
            @Override
            public UUID getBundleId() {
                return bundleId;
            }
        };
    }

    protected ExistingEvent createExistingEventForAssertion(final SubscriptionTransitionType type, 
            final String productName, final PhaseType phaseType, final ProductCategory category, final String priceListName, final BillingPeriod billingPeriod,
            final DateTime effectiveDateTime) {

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
        ExistingEvent ev = new ExistingEvent() {
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
        };
        return ev;
    }
    
    protected SubscriptionRepair getSubscriptionRepair(final UUID id, final BundleRepair bundleRepair) {
        for (SubscriptionRepair cur : bundleRepair.getSubscriptions()) {
            if (cur.getId().equals(id)) {
                return cur;
            }
        }
        Assert.fail("Failed to find SubscriptionReapir " + id);
        return null;
    }
    protected void validateExistingEventForAssertion(final ExistingEvent expected, final ExistingEvent input) {
        assertEquals(input.getPlanPhaseSpecifier().getProductName(), expected.getPlanPhaseSpecifier().getProductName());
        assertEquals(input.getPlanPhaseSpecifier().getPhaseType(), expected.getPlanPhaseSpecifier().getPhaseType());
        assertEquals(input.getPlanPhaseSpecifier().getProductCategory(), expected.getPlanPhaseSpecifier().getProductCategory());                    
        assertEquals(input.getPlanPhaseSpecifier().getPriceListName(), expected.getPlanPhaseSpecifier().getPriceListName());                    
        assertEquals(input.getPlanPhaseSpecifier().getBillingPeriod(), expected.getPlanPhaseSpecifier().getBillingPeriod());
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

    protected void sortEventsOnBundle(final BundleRepair bundle) {
        if (bundle.getSubscriptions() == null) {
            return;
        }
        for (SubscriptionRepair cur : bundle.getSubscriptions()) {
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
            public int compare(ExistingEvent arg0, ExistingEvent arg1) {
                return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
            }
        });
    }
    protected void sortNewEvent(final List<NewEvent> events) {
        Collections.sort(events, new Comparator<NewEvent>() {
            @Override
            public int compare(NewEvent arg0, NewEvent arg1) {
                return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
            }
        });
    }

}
