/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

public class DefaultSubscriptionBillingEvent implements SubscriptionBillingEvent {

    private final SubscriptionBaseTransitionType type;
    private final String planName;
    private final String planPhaseName;
    private final DateTime effectiveDate;
    private final Long totalOrdering;
    private final DateTime lastChangePlanDate;
    private final Integer bcdLocal;

    public DefaultSubscriptionBillingEvent(final SubscriptionBaseTransitionType type, final String planName, final String planPhaseName, final DateTime effectiveDate, final Long totalOrdering, final DateTime lastChangePlanDate, final Integer bcdLocal) {
        this.type = type;
        this.planName = planName;
        this.planPhaseName = planPhaseName;
        this.effectiveDate = effectiveDate;
        this.totalOrdering = totalOrdering;
        this.lastChangePlanDate = lastChangePlanDate;
        this.bcdLocal = bcdLocal;
    }

    @Override
    public SubscriptionBaseTransitionType getType() {
        return type;
    }

    @Override
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPlanPhaseName() {
        return planPhaseName;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public Long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public DateTime getLastChangePlanDate() {
        return lastChangePlanDate;
    }

    @Override
    public Integer getBcdLocal() {
        return bcdLocal;
    }

    @Override
    public String toString() {
        return "DefaultSubscriptionBillingEvent{" +
               "type=" + type +
               ", planName='" + planName + '\'' +
               ", planPhaseName='" + planPhaseName + '\'' +
               ", effectiveDate=" + effectiveDate +
               ", totalOrdering=" + totalOrdering +
               ", lastChangePlanDate=" + lastChangePlanDate +
               ", bcdLocal=" + bcdLocal +
               '}';
    }
}
