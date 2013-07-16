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

package com.ning.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.entitlement.block.BlockingChecker;
import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionSourceType;
import com.ning.billing.subscription.api.user.SubscriptionState;
import com.ning.billing.subscription.api.user.SubscriptionTransition;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

public class BlockingSubscription implements Subscription {

    private final Subscription subscription;
    private final BlockingInternalApi blockingApi;
    private final BlockingChecker checker;
    private final InternalTenantContext context;
    private final InternalCallContextFactory internalCallContextFactory;

    private BlockingState blockingState = null;

    public BlockingSubscription(final Subscription subscription, final BlockingInternalApi blockingApi, final BlockingChecker checker,
                                final InternalTenantContext context, final InternalCallContextFactory internalCallContextFactory) {
        this.subscription = subscription;
        this.blockingApi = blockingApi;
        this.checker = checker;
        this.context = context;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public UUID getId() {
        return subscription.getId();
    }

    @Override
    public DateTime getCreatedDate() {
        return subscription.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return subscription.getUpdatedDate();
    }

    @Override
    public boolean cancel(final DateTime requestedDate, final CallContext context) throws SubscriptionUserApiException {
        return subscription.cancel(requestedDate, context);
    }

    @Override
    public boolean cancelWithPolicy(DateTime requestedDate, ActionPolicy policy, CallContext context)
            throws SubscriptionUserApiException {
        return subscription.cancelWithPolicy(requestedDate, policy, context);
    }

    @Override
    public boolean uncancel(final CallContext context) throws SubscriptionUserApiException {
        return subscription.uncancel(context);
    }

    @Override
    public boolean changePlan(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate,
                              final CallContext context) throws SubscriptionUserApiException {
        try {
            checker.checkBlockedChange(this, internalCallContextFactory.createInternalTenantContext(context));
        } catch (BlockingApiException e) {
            throw new SubscriptionUserApiException(e, e.getCode(), e.getMessage());
        }
        return subscription.changePlan(productName, term, priceList, requestedDate, context);
    }

    @Override
    public boolean changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList,
                                        final DateTime requestedDate, final ActionPolicy policy, final CallContext context) throws SubscriptionUserApiException {
        try {
            checker.checkBlockedChange(this, internalCallContextFactory.createInternalTenantContext(context));
        } catch (BlockingApiException e) {
            throw new SubscriptionUserApiException(e, e.getCode(), e.getMessage());
        }
        return subscription.changePlanWithPolicy(productName, term, priceList, requestedDate, policy, context);
    }

    @Override
    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException {
        return subscription.recreate(spec, requestedDate, context);
    }

    @Override
    public UUID getBundleId() {
        return subscription.getBundleId();
    }

    @Override
    public SubscriptionState getState() {
        return subscription.getState();
    }

    @Override
    public SubscriptionSourceType getSourceType() {
        return subscription.getSourceType();
    }

    @Override
    public DateTime getStartDate() {
        return subscription.getStartDate();
    }

    @Override
    public DateTime getEndDate() {
        return subscription.getEndDate();
    }

    @Override
    public DateTime getFutureEndDate() {
        return subscription.getFutureEndDate();
    }

    @Override
    public Plan getCurrentPlan() {
        return subscription.getCurrentPlan();
    }

    @Override
    public Plan getLastActivePlan() {
        return subscription.getLastActivePlan();
    }


    @Override
    public PriceList getCurrentPriceList() {
        return subscription.getCurrentPriceList();
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return subscription.getCurrentPhase();
    }

    @Override
    public DateTime getChargedThroughDate() {
        return subscription.getChargedThroughDate();
    }

    @Override
    public DateTime getPaidThroughDate() {
        return subscription.getPaidThroughDate();
    }

    @Override
    public ProductCategory getCategory() {
        return subscription.getCategory();
    }

    @Override
    public BlockingState getBlockingState() {
        if (blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(this, context);
        }
        return blockingState;
    }

    @Override
    public String getLastActiveProductName() {
        return subscription.getLastActiveProductName();
    }

    @Override
    public String getLastActivePriceListName() {
        return subscription.getLastActivePriceListName();
    }

    @Override
    public String getLastActiveCategoryName() {
        return subscription.getLastActiveCategoryName();
    }

    @Override
    public String getLastActiveBillingPeriod() {
        return subscription.getLastActiveBillingPeriod();
    }

    @Override
    public SubscriptionTransition getPendingTransition() {
        return subscription.getPendingTransition();
    }

    @Override
    public SubscriptionTransition getPreviousTransition() {
        return subscription.getPreviousTransition();
    }

    @Override
    public List<SubscriptionTransition> getAllTransitions() {
        return subscription.getAllTransitions();
    }

    public Subscription getDelegateSubscription() {
        return subscription;
    }
}
