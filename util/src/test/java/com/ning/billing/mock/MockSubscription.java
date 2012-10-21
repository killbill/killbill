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
import org.mockito.Mockito;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;

import com.google.common.collect.ImmutableList;

public class MockSubscription implements Subscription {

    private final UUID id;
    private final UUID bundleId;
    private final SubscriptionState state;
    private Plan plan;
    private final PlanPhase phase;
    private final DateTime startDate;
    private final List<EffectiveSubscriptionInternalEvent> transitions;

    public MockSubscription(final UUID id, final UUID bundleId, final Plan plan, final DateTime startDate, final List<EffectiveSubscriptionInternalEvent> transitions) {
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
        this.transitions = ImmutableList.<EffectiveSubscriptionInternalEvent>of();
    }

    Subscription sub = Mockito.mock(Subscription.class);

    @Override
    public boolean cancel(final DateTime requestedDate, final CallContext context) throws EntitlementUserApiException {
        return sub.cancel(requestedDate, context);
    }

    @Override
    public boolean cancelWithPolicy(DateTime requestedDate,
                                    ActionPolicy policy, CallContext context)
            throws EntitlementUserApiException {
        return sub.cancelWithPolicy(requestedDate, policy, context);
    }

    @Override
    public boolean uncancel(final CallContext context) throws EntitlementUserApiException {
        return sub.uncancel(context);
    }

    @Override
    public boolean changePlan(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate,
                              final CallContext context) throws EntitlementUserApiException {
        return sub.changePlan(productName, term, priceList, requestedDate, context);
    }

    @Override
    public boolean changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList,
                                        final DateTime requestedDate, final ActionPolicy policy, final CallContext context) throws EntitlementUserApiException {
        return sub.changePlan(productName, term, priceList, requestedDate, context);
    }

    @Override
    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws EntitlementUserApiException {
        return sub.recreate(spec, requestedDate, context);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DateTime getUpdatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public SubscriptionState getState() {
        return state;
    }

    @Override
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return sub.getEndDate();
    }

    @Override
    public Plan getCurrentPlan() {
        return plan;
    }

    @Override
    public BlockingState getBlockingState() {
        return sub.getBlockingState();
    }

    @Override
    public PriceList getCurrentPriceList() {
        return new MockPriceList();
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return phase;
    }

    @Override
    public DateTime getChargedThroughDate() {
        return sub.getChargedThroughDate();
    }

    @Override
    public DateTime getPaidThroughDate() {
        return sub.getPaidThroughDate();
    }

    @Override
    public ProductCategory getCategory() {
        return sub.getCategory();
    }

    @Override
    public EffectiveSubscriptionInternalEvent getPendingTransition() {
        return sub.getPendingTransition();
    }

    @Override
    public EffectiveSubscriptionInternalEvent getPreviousTransition() {
        return sub.getPreviousTransition();
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions() {
        return transitions;
    }

    @Override
    public List<EffectiveSubscriptionInternalEvent> getAllTransitions() {
        return transitions;
    }

    @Override
    public DateTime getFutureEndDate() {
        return null;
    }

    @Override
    public SubscriptionSourceType getSourceType() {
        return null;
    }

    @Override
    public String getLastActiveProductName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLastActivePriceListName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLastActiveCategoryName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLastActiveBillingPeriod() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setPlan(final Plan plan) {
        this.plan = plan;
    }
}
