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

package org.killbill.billing.subscription.events;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;

public abstract class EventBase implements SubscriptionBaseEvent {

    private final UUID uuid;
    private final UUID subscriptionId;
    private final DateTime createdDate;
    private final DateTime updatedDate;
    private final DateTime effectiveDate;

    private final long totalOrdering;
    private final boolean isActive;

    public EventBase(final EventBaseBuilder<?> builder) {
        this.totalOrdering = builder.getTotalOrdering();
        this.uuid = builder.getUuid();
        this.subscriptionId = builder.getSubscriptionId();
        this.createdDate = builder.getCreatedDate();
        this.updatedDate = builder.getUpdatedDate();
        this.effectiveDate = builder.getEffectiveDate();
        this.isActive = builder.isActive();
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }


    @Override
    public int compareTo(final SubscriptionBaseEvent other) {
        if (other == null) {
            throw new IllegalArgumentException("IEvent is compared to a null instance");
        }

        // If events are for different subscriptions order per subscriptionId first
        final int subCmp = subscriptionId.compareTo(other.getSubscriptionId());
        if (subCmp != 0) {
            return subCmp;
        }

        // Order by effective date first
        if (effectiveDate.isBefore(other.getEffectiveDate())) {
            return -1;
        } else if (effectiveDate.isAfter(other.getEffectiveDate())) {
            return 1;
        }

        //
        // In case of date equality:
        //

        // We first ensure that first event is a start event (CREATE or TRANSFER)
        final boolean isCurTransferOrCreate = getType() == EventType.API_USER &&
                                              (((ApiEvent) this).getApiEventType() == ApiEventType.CREATE || ((ApiEvent) this).getApiEventType() == ApiEventType.TRANSFER);
        final boolean isOtherTransferOrCreate = other.getType() == EventType.API_USER &&
                                                (((ApiEvent) other).getApiEventType() == ApiEventType.CREATE || ((ApiEvent) other).getApiEventType() == ApiEventType.TRANSFER);
        if (isCurTransferOrCreate) {
            return -1;
        } else if (isOtherTransferOrCreate) {
            return 1;
        }
        // Then we rely on the totalOrdering (recordId unless in-memory event for which it is 0)
        if (getTotalOrdering() == 0 && other.getTotalOrdering() > 0) {
            return 1;
        } else if (getTotalOrdering() > 0 && other.getTotalOrdering() == 0) {
            return -1;
        } else if (getTotalOrdering() == other.getTotalOrdering()) {
            return 0;
        } else {
            return getTotalOrdering() < (other.getTotalOrdering()) ? -1 : 1;
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof SubscriptionBaseEvent)) {
            return false;
        }
        return (this.compareTo((SubscriptionBaseEvent) other) == 0);
    }

    // Fix spotbugs' "HE_INHERITS_EQUALS_USE_HASHCODE". Before and after changes tested in
    // TestBCDEventData#testEqualsAndHashcode().
    // This changes applied to spotbugs warning to following classes: ExpiredEventData, PhaseEventData, QuantityEventData,
    // ApiEventBase, ApiEventCancel, ApiEventChange, ApiEventCreate, ApiEventTransfer, ApiEventUncancel, ApiEventUndoChange
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public abstract EventType getType();
}
