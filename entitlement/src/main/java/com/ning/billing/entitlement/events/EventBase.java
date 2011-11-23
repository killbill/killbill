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

package com.ning.billing.entitlement.events;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.entitlement.events.IEventLyfecycle.IEventLyfecycleState;
import com.ning.billing.entitlement.events.user.IApiEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;

public abstract class EventBase implements IEvent {

    private final UUID uuid;
    private final UUID subscriptionId;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final DateTime processedDate;

    // Lifecyle of the event
    private long activeVersion;
    private boolean isActive;
    private UUID processingOwner;
    private DateTime nextAvailableProcessingTime;
    private IEventLyfecycleState processingState;

    public EventBase(UUID subscriptionId, DateTime requestedDate,
            DateTime effectiveDate, DateTime processedDate,
            long activeVersion, boolean isActive) {
        this(subscriptionId, requestedDate, effectiveDate, processedDate, activeVersion, isActive, null, null, IEventLyfecycleState.AVAILABLE);
    }

    private EventBase(UUID subscriptionId, DateTime requestedDate,
            DateTime effectiveDate, DateTime processedDate,
            long activeVersion, boolean isActive,
            UUID processingOwner, DateTime nextAvailableProcessingTime,
            IEventLyfecycleState processingState) {
        this(UUID.randomUUID(), subscriptionId, requestedDate, effectiveDate, processedDate, activeVersion, isActive,
                processingOwner, nextAvailableProcessingTime, processingState);
    }

    public EventBase(UUID id, UUID subscriptionId, DateTime requestedDate,
            DateTime effectiveDate, DateTime processedDate,
            long activeVersion, boolean isActive,
            UUID processingOwner, DateTime nextAvailableProcessingTime,
            IEventLyfecycleState processingState) {
        this.uuid = id;
        this.subscriptionId = subscriptionId;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.processedDate = processedDate;

        this.activeVersion = activeVersion;
        this.isActive = isActive;
        this.processingOwner = processingOwner;
        this.nextAvailableProcessingTime = nextAvailableProcessingTime;
        this.processingState = processingState;

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
    public UUID getId() {
        return uuid;
    }


    @Override
    public long getActiveVersion() {
        return activeVersion;
    }

    @Override
    public void setActiveVersion(long activeVersion) {
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
    public UUID getOwner() {
        return processingOwner;
    }

    @Override
    public void setOwner(UUID owner) {
        this.processingOwner = owner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return nextAvailableProcessingTime;
    }

    @Override
    public void setNextAvailableDate(DateTime dateTime) {
        this.nextAvailableProcessingTime = dateTime;
    }


    @Override
    public IEventLyfecycleState getProcessingState() {
        return processingState;
    }

    @Override
    public void setProcessingState(IEventLyfecycleState processingState) {
        this.processingState = processingState;
    }

    @Override
    public boolean isAvailableForProcessing(DateTime now) {

        // Event got deactivated, will never be processed
        if (!isActive) {
            return false;
        }

        switch(processingState) {
        case AVAILABLE:
            break;
        case IN_PROCESSING:
            // Somebody already got the event, not available yet
            if (nextAvailableProcessingTime.isAfter(now)) {
                return false;
            }
            break;
        case PROCESSED:
            return false;
        default:
            throw new EntitlementError(String.format("Unkwnon IEvent processing state %s", processingState));
        }
        return effectiveDate.isBefore(now);
    }

    //
    // Really used for unit tesrs only as the sql implementation relies on date first and then event insertion
    //
    // Order first by:
    // - effectiveDate, followed by processedDate, requestedDate
    // - if all dates are equal-- unlikely, we first return PHASE EVENTS
    // - If both events are User events, return the first CREATE, CHANGE,... as specified by ApiEventType
    // - If all that is not enough return consistent by random ordering based on UUID
    //
    @Override
    public int compareTo(IEvent other) {
        if (other == null) {
            throw new NullPointerException("IEvent is compared to a null instance");
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
            return ((IApiEvent) this).getEventType().compareTo(((IApiEvent) other).getEventType());
        } else {
            return uuid.compareTo(other.getId());
        }
    }


    @Override
    public boolean equals(Object other) {
      if (! (other instanceof IEvent)) {
          return false;
      }
      return (this.compareTo((IEvent) other) == 0);
    }

    @Override
    public abstract EventType getType();


}
