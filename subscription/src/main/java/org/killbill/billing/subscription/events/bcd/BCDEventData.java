/*
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

package org.killbill.billing.subscription.events.bcd;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.events.EventBase;

public class BCDEventData extends EventBase implements BCDEvent {

    private final Integer billCycleDayLocal;

    public BCDEventData(final BCDEventBuilder builder) {
        super(builder);
        this.billCycleDayLocal = builder.getBillCycleDayLocal();
    }

    @Override
    public EventType getType() {
        return EventType.BCD_UPDATE;
    }

    @Override
    public String toString() {
        return "BCDEventData {" +
               "uuid=" + getId() +
               ", subscriptionId=" + getSubscriptionId() +
               ", createdDate=" + getCreatedDate() +
               ", updatedDate=" + getUpdatedDate() +
               ", effectiveDate=" + getEffectiveDate() +
               ", totalOrdering=" + getTotalOrdering() +
               ", isActive=" + isActive() +
               '}';
    }

    // Hack until we introduce a proper field for that
    @Override
    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    public static BCDEvent createBCDEvent(final DefaultSubscriptionBase subscription, final DateTime effectiveDate, final int billCycleDayLocal) {
        return new BCDEventData(new BCDEventBuilder()
                                        .setSubscriptionId(subscription.getId())
                                        .setEffectiveDate(effectiveDate)
                                        .setActive(true)
                                        .setBillCycleDayLocal(billCycleDayLocal));
    }

}
