/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

public class DefaultSubscription extends DefaultEntitlement implements Subscription {

    DefaultSubscription(final DefaultEntitlement entitlement) {
        super(entitlement);
    }

    @Override
    public LocalDate getBillingStartDate() {
        return internalTenantContext.toLocalDate(getSubscriptionBase().getStartDate());
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

        return futureOrCurrentEndDate != null ? internalTenantContext.toLocalDate(futureOrCurrentEndDate) : null;
    }

    @Override
    public LocalDate getChargedThroughDate() {
        return getSubscriptionBase().getChargedThroughDate() != null ? internalTenantContext.toLocalDate(getSubscriptionBase().getChargedThroughDate()) : null;
    }

    @Override
    public List<SubscriptionEvent> getSubscriptionEvents() {
        return SubscriptionEventOrdering.sortedCopy(this, internalTenantContext);
    }
}
