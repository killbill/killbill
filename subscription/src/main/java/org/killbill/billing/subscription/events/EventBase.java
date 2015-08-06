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

public abstract class EventBase implements SubscriptionBaseEvent {

    private final UUID uuid;
    private final UUID subscriptionId;
    private final DateTime createdDate;
    private final DateTime updatedDate;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final DateTime processedDate;

    private long totalOrdering;
    private long activeVersion;
    private boolean isActive;

    public EventBase(final EventBaseBuilder<?> builder) {
        this.totalOrdering = builder.getTotalOrdering();
        this.uuid = builder.getUuid();
        this.subscriptionId = builder.getSubscriptionId();
        this.createdDate = builder.getCreatedDate();
        this.updatedDate = builder.getUpdatedDate();
        this.requestedDate = builder.getRequestedDate();
        this.effectiveDate = builder.getEffectiveDate();
        this.processedDate = builder.getProcessedDate();
        this.activeVersion = builder.getActiveVersion();
        this.isActive = builder.isActive();
    }

    @Override
    public DateTime getRequestedDate() {
        return requestedDate;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public DateTime getProcessedDate() {
        return processedDate;
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
    public long getActiveVersion() {
        return activeVersion;
    }

    @Override
    public void setActiveVersion(final long activeVersion) {
        this.activeVersion = activeVersion;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void deactivate() {
        this.isActive = false;
    }

    @Override
    public void reactivate() {
        this.isActive = true;
    }

    @Override
    public void setTotalOrdering(final long totalOrdering) {
        this.totalOrdering = totalOrdering;
    }

    //
    // Really used for unit tests only as the sql implementation relies on date first and then event insertion
    //
    // Order first by:
    // - effectiveDate, followed by processedDate, requestedDate
    // - if all dates are equal-- unlikely, we first return PHASE EVENTS
    // - If both events are User events, return the first CREATE, CHANGE,... as specified by ApiEventType
    // - If all that is not enough return consistent by random ordering based on UUID
    //
    @Override
    public int compareTo(final SubscriptionBaseEvent other) {
        if (other == null) {
            throw new IllegalArgumentException("IEvent is compared to a null instance");
        }

        if (effectiveDate.isBefore(other.getEffectiveDate())) {
            return -1;
        } else if (effectiveDate.isAfter(other.getEffectiveDate())) {
            return 1;
        } else if (processedDate.isBefore(other.getProcessedDate())) {
            return -1;
        } else if (processedDate.isAfter(other.getProcessedDate())) {
            return 1;
        } else if (requestedDate.isBefore(other.getRequestedDate())) {
            return -1;
        } else if (requestedDate.isAfter(other.getRequestedDate())) {
            return 1;
        } else if (getType() != other.getType()) {
            return (getType() == EventType.PHASE) ? -1 : 1;
        } else if (getType() == EventType.API_USER) {
            return ((ApiEvent) this).getEventType().compareTo(((ApiEvent) other).getEventType());
        } else {
            return uuid.compareTo(other.getId());
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof SubscriptionBaseEvent)) {
            return false;
        }
        return (this.compareTo((SubscriptionBaseEvent) other) == 0);
    }

    @Override
    public abstract EventType getType();
}
