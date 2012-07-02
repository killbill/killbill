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

package com.ning.billing.mock;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.collect.ImmutableList;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.CallContext;

public class MockSubscription implements Subscription {
    private final UUID id;
    private final UUID bundleId;
    private final SubscriptionState state;
    private final Plan plan;
    private final PlanPhase phase;
    private final DateTime startDate;
    private final List<EffectiveSubscriptionEvent> transitions;

    public MockSubscription(final UUID id, final UUID bundleId, final Plan plan, final DateTime startDate, final List<EffectiveSubscriptionEvent> transitions) {
        this.id = id;
        this.bundleId = bundleId;
        this.state = SubscriptionState.ACTIVE;
        this.plan = plan;
        this.phase = null;
        this.startDate = startDate;
        this.transitions = transitions;
    }

    public MockSubscription(final SubscriptionState state, final Plan plan, final PlanPhase phase) {
        this.id = UUID.randomUUID();
        this.bundleId = UUID.randomUUID();
        this.state = state;
        this.plan = plan;
        this.phase = phase;
        this.startDate = new DateTime(DateTimeZone.UTC);
        this.transitions = ImmutableList.<EffectiveSubscriptionEvent>of();
    }

    Subscription sub = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);

    public boolean cancel(final DateTime requestedDate, final boolean eot, final CallContext context) throws EntitlementUserApiException {
        return sub.cancel(requestedDate, eot, context);
    }

    public boolean uncancel(final CallContext context) throws EntitlementUserApiException {
        return sub.uncancel(context);
    }

    public boolean changePlan(final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate,
                              final CallContext context) throws EntitlementUserApiException {
        return sub.changePlan(productName, term, planSet, requestedDate, context);
    }

    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws EntitlementUserApiException {
        return sub.recreate(spec, requestedDate, context);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public SubscriptionState getState() {
        return state;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return sub.getEndDate();
    }

    public Plan getCurrentPlan() {
        return plan;
    }

    public BlockingState getBlockingState() {
        return sub.getBlockingState();
    }

    public PriceList getCurrentPriceList() {
        return sub.getCurrentPriceList();
    }

    public PlanPhase getCurrentPhase() {
        return phase;
    }

    public DateTime getChargedThroughDate() {
        return sub.getChargedThroughDate();
    }

    public DateTime getPaidThroughDate() {
        return sub.getPaidThroughDate();
    }

    public ProductCategory getCategory() {
        return sub.getCategory();
    }

    public EffectiveSubscriptionEvent getPendingTransition() {
        return sub.getPendingTransition();
    }

    public EffectiveSubscriptionEvent getPreviousTransition() {
        return sub.getPreviousTransition();
    }

    public List<EffectiveSubscriptionEvent> getBillingTransitions() {
        return transitions;
    }

    @Override
    public List<EffectiveSubscriptionEvent> getAllTransitions() {
        return transitions;
    }
}
