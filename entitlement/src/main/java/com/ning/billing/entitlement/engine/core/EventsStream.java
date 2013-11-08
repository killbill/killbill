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

package com.ning.billing.entitlement.engine.core;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class EventsStream {

    private final Account account;
    private final SubscriptionBaseBundle bundle;
    private final List<BlockingState> subscriptionEntitlementStates;
    private final List<BlockingState> bundleEntitlementStates;
    private final List<BlockingState> accountEntitlementStates;
    private final BlockingAggregator blockingAggregator;
    private final SubscriptionBase subscription;
    private final InternalTenantContext internalTenantContext;
    private final DateTime utcNow;

    private LocalDate entitlementEffectiveEndDate;
    private BlockingState entitlementCancelEvent;
    private EntitlementState entitlementState;

    public EventsStream(final Account account, final SubscriptionBaseBundle bundle,
                        final List<BlockingState> subscriptionEntitlementStates, final List<BlockingState> bundleEntitlementStates,
                        final List<BlockingState> accountEntitlementStates, final BlockingAggregator blockingAggregator,
                        final SubscriptionBase subscription, final InternalTenantContext contextWithValidAccountRecordId, final DateTime utcNow) {
        this.account = account;
        this.bundle = bundle;
        this.subscriptionEntitlementStates = subscriptionEntitlementStates;
        this.bundleEntitlementStates = bundleEntitlementStates;
        this.accountEntitlementStates = accountEntitlementStates;
        this.blockingAggregator = blockingAggregator;
        this.subscription = subscription;
        this.internalTenantContext = contextWithValidAccountRecordId;
        this.utcNow = utcNow;

        setup();
    }

    public Account getAccount() {
        return account;
    }

    public SubscriptionBaseBundle getBundle() {
        return bundle;
    }

    public SubscriptionBase getSubscription() {
        return subscription;
    }

    public InternalTenantContext getInternalTenantContext() {
        return internalTenantContext;
    }

    public LocalDate getEntitlementEffectiveEndDate() {
        return entitlementEffectiveEndDate;
    }

    public BlockingState getEntitlementCancelEvent() {
        return entitlementCancelEvent;
    }

    public EntitlementState getEntitlementState() {
        return entitlementState;
    }

    public BlockingAggregator getCurrentBlockingAggregator() {
        return blockingAggregator;
    }

    public boolean isFutureEntitlementCancelled() {
        return entitlementCancelEvent != null && entitlementCancelEvent.getEffectiveDate().isAfter(utcNow);
    }

    public boolean isEntitlementActive() {
        return entitlementState == EntitlementState.ACTIVE;
    }

    public boolean isEntitlementCancelled() {
        return entitlementState == EntitlementState.CANCELLED;
    }

    public SubscriptionBaseTransition getPendingSubscriptionEvents(final SubscriptionBaseTransitionType... types) {
        final List<SubscriptionBaseTransitionType> typeList = ImmutableList.<SubscriptionBaseTransitionType>copyOf(types);
        return Iterables.<SubscriptionBaseTransition>tryFind(subscription.getAllTransitions(),
                                                             new Predicate<SubscriptionBaseTransition>() {
                                                                 @Override
                                                                 public boolean apply(final SubscriptionBaseTransition input) {
                                                                     return input.getEffectiveTransitionTime().isAfter(utcNow) && typeList.contains(input.getTransitionType());
                                                                 }
                                                             }).orNull();
    }

    private void setup() {
        computeEntitlementEffectiveEndDate();
        computeEntitlementCancelEvent();
        computeStateForEntitlement();
    }

    private void computeEntitlementEffectiveEndDate() {
        LocalDate result = null;
        BlockingState lastEntry;

        lastEntry = (!subscriptionEntitlementStates.isEmpty()) ? subscriptionEntitlementStates.get(subscriptionEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            result = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
        }

        lastEntry = (!bundleEntitlementStates.isEmpty()) ? bundleEntitlementStates.get(bundleEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            final LocalDate localDate = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
            result = ((result == null) || (localDate.compareTo(result) < 0)) ? localDate : result;
        }

        lastEntry = (!accountEntitlementStates.isEmpty()) ? accountEntitlementStates.get(accountEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            final LocalDate localDate = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
            result = ((result == null) || (localDate.compareTo(result) < 0)) ? localDate : result;
        }

        entitlementEffectiveEndDate = result;
    }

    private void computeEntitlementCancelEvent() {
        entitlementCancelEvent = Iterables.<BlockingState>tryFind(subscriptionEntitlementStates,
                                                                  new Predicate<BlockingState>() {
                                                                      @Override
                                                                      public boolean apply(final BlockingState input) {
                                                                          return EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                 DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName());
                                                                      }
                                                                  }).orNull();
    }

    private void computeStateForEntitlement() {
        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (entitlementEffectiveEndDate != null && entitlementEffectiveEndDate.compareTo(new LocalDate(utcNow, account.getTimeZone())) <= 0) {
            entitlementState = EntitlementState.CANCELLED;
        } else {
            // Gather states across all services and check if one of them is set to 'blockEntitlement'
            entitlementState = (blockingAggregator != null && blockingAggregator.isBlockEntitlement() ? EntitlementState.BLOCKED : EntitlementState.ACTIVE);
        }
    }
}
