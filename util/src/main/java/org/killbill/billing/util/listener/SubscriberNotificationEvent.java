/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.listener;

import org.killbill.bus.api.BusEvent;
import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SubscriberNotificationEventDeserializer.class)
public class SubscriberNotificationEvent implements NotificationEvent {

    private final BusEvent busEvent;
    private final Class busEventClass;

    @JsonCreator
    public SubscriberNotificationEvent(@JsonProperty("busEvent") final BusEvent busEvent,
                                       @JsonProperty("busEventClass") final Class busEventClass) {
        this.busEvent = busEvent;
        this.busEventClass = busEventClass;
    }

    public BusEvent getBusEvent() {
        return busEvent;
    }

    public Class getBusEventClass() {
        return busEventClass;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubscriberNotificationEvent{");
        sb.append("busEvent=").append(busEvent);
        sb.append(", busEventClass=").append(busEventClass);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriberNotificationEvent that = (SubscriberNotificationEvent) o;

        if (busEvent != null ? !busEvent.equals(that.busEvent) : that.busEvent != null) {
            return false;
        }
        return busEventClass != null ? busEventClass.equals(that.busEventClass) : that.busEventClass == null;
    }

    @Override
    public int hashCode() {
        int result = busEvent != null ? busEvent.hashCode() : 0;
        result = 31 * result + (busEventClass != null ? busEventClass.hashCode() : 0);
        return result;
    }
}
