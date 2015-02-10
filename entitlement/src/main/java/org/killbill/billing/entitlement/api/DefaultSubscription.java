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

package org.killbill.billing.entitlement.api;

import java.util.Collection;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class DefaultSubscription extends DefaultEntitlement implements Subscription {

    private final Collection<BlockingState> currentSubscriptionBlockingStatesForServices;

    DefaultSubscription(final DefaultEntitlement entitlement) {
        super(entitlement);
        this.currentSubscriptionBlockingStatesForServices = eventsStream.getCurrentSubscriptionEntitlementBlockingStatesForServices();
    }

    @Override
    public LocalDate getBillingStartDate() {
        return new LocalDate(getSubscriptionBase().getStartDate(), getAccountTimeZone());
    }

    @Override
    public LocalDate getBillingEndDate() {
        final DateTime futureOrCurrentEndDateForSubscription = getSubscriptionBase().getEndDate() != null ? getSubscriptionBase().getEndDate() : getSubscriptionBase().getFutureEndDate();
        final DateTime futureOrCurrentEndDateForBaseSubscription;
        if (getBasePlanSubscriptionBase() == null) {
            futureOrCurrentEndDateForBaseSubscription = null;
        } else {
            futureOrCurrentEndDateForBaseSubscription = getBasePlanSubscriptionBase().getEndDate() != null ? getBasePlanSubscriptionBase().getEndDate() : getBasePlanSubscriptionBase().getFutureEndDate();
        }

        final DateTime futureOrCurrentEndDate;
        if (futureOrCurrentEndDateForBaseSubscription != null && futureOrCurrentEndDateForBaseSubscription.isBefore(futureOrCurrentEndDateForSubscription)) {
            futureOrCurrentEndDate = futureOrCurrentEndDateForBaseSubscription;
        } else {
            futureOrCurrentEndDate = futureOrCurrentEndDateForSubscription;
        }

        return futureOrCurrentEndDate != null ? new LocalDate(futureOrCurrentEndDate, getAccountTimeZone()) : null;
    }

    @Override
    public LocalDate getChargedThroughDate() {
        return getSubscriptionBase().getChargedThroughDate() != null ? new LocalDate(getSubscriptionBase().getChargedThroughDate(), getAccountTimeZone()) : null;
    }

    @Override
    public String getCurrentStateForService(final String serviceName) {
        if (currentSubscriptionBlockingStatesForServices == null) {
            return null;
        } else {
            final BlockingState blockingState = Iterables.<BlockingState>tryFind(currentSubscriptionBlockingStatesForServices,
                                                                                 new Predicate<BlockingState>() {
                                                                                     @Override
                                                                                     public boolean apply(final BlockingState input) {
                                                                                         return serviceName.equals(input.getService());
                                                                                     }
                                                                                 }).orNull();
            return blockingState == null ? null : blockingState.getService();
        }
    }

    @Override
    public List<SubscriptionEvent> getSubscriptionEvents() {
        return SubscriptionEventOrdering.sortedCopy(this, getAccountTimeZone());
    }
}
