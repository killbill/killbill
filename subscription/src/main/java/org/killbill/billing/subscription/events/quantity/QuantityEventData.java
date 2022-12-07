/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.subscription.events.quantity;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.events.EventBase;

public class QuantityEventData extends EventBase implements QuantityEvent {

    private final Integer quantity;

    public QuantityEventData(final QuantityEventBuilder builder) {
        super(builder);
        this.quantity = builder.getQuantity();
    }

    @Override
    public EventType getType() {
        return EventType.QUANTITY_UPDATE;
    }

    @Override
    public String toString() {
        return "QuantityEventData {" +
               "uuid=" + getId() +
               ", subscriptionId=" + getSubscriptionId() +
               ", createdDate=" + getCreatedDate() +
               ", updatedDate=" + getUpdatedDate() +
               ", effectiveDate=" + getEffectiveDate() +
               ", totalOrdering=" + getTotalOrdering() +
               ", isActive=" + isActive() +
               '}';
    }

    @Override
    public Integer getQuantity() {
        return quantity;
    }

    public static QuantityEventData createQuantityEvent(final DefaultSubscriptionBase subscription, final DateTime effectiveDate, final int quantity) {
        return new QuantityEventData(new QuantityEventBuilder()
                                        .setSubscriptionId(subscription.getId())
                                        .setEffectiveDate(effectiveDate)
                                        .setActive(true)
                                        .setQuantity(quantity));
    }

}
