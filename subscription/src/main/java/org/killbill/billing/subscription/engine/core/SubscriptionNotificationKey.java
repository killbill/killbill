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

package org.killbill.billing.subscription.engine.core;

import java.util.UUID;

import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionNotificationKey implements NotificationEvent {

    private final UUID eventId;
    private final int seqId;


    @JsonCreator
    public SubscriptionNotificationKey(@JsonProperty("eventId") final UUID eventId,
                                       @JsonProperty("seqId") final int seqId) {
        this.eventId = eventId;
        this.seqId = seqId;
    }

    public SubscriptionNotificationKey(final UUID eventId) {
        this(eventId, 0);
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getSeqId() {
        return seqId;
    }

    public String toString() {
        if (seqId == 0) {
            return eventId.toString();
        } else {
            return eventId.toString() + ":" + seqId;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((eventId == null) ? 0 : eventId.hashCode());
        result = prime * result + seqId;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SubscriptionNotificationKey other = (SubscriptionNotificationKey) obj;
        if (eventId == null) {
            if (other.eventId != null) {
                return false;
            }
        } else if (!eventId.equals(other.eventId)) {
            return false;
        }
        if (seqId != other.seqId) {
            return false;
        }
        return true;
    }
}
