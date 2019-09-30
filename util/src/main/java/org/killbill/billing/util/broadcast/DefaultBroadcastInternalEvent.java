/*
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

package org.killbill.billing.util.broadcast;

import org.killbill.billing.events.BroadcastInternalEvent;
import org.killbill.billing.events.BusEventBase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultBroadcastInternalEvent extends BusEventBase implements BroadcastInternalEvent {

    private String serviceName;
    private String type;
    private String jsonEvent;

    public DefaultBroadcastInternalEvent() {
        super(null, 0L, null);
    }

    @JsonCreator
    public DefaultBroadcastInternalEvent(@JsonProperty("serviceName") final String serviceName,
                                         @JsonProperty("type") final String type,
                                         @JsonProperty("jsonEvent") final String jsonEvent) {
        super(null, 0L, null);
        this.serviceName = serviceName;
        this.type = type;
        this.jsonEvent = jsonEvent;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getJsonEvent() {
        return jsonEvent;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.BROADCAST_SERVICE;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DefaultBroadcastInternalEvent{");
        sb.append("serviceName='").append(serviceName).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append(", jsonEvent='").append(jsonEvent).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
