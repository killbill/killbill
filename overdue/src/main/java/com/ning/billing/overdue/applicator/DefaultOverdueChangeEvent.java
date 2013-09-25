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

package com.ning.billing.overdue.applicator;

import java.util.UUID;

import com.ning.billing.events.BusEventBase;
import com.ning.billing.events.OverdueChangeInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultOverdueChangeEvent extends BusEventBase implements OverdueChangeInternalEvent {

    private final UUID overdueObjectId;
    private final String previousOverdueStateName;
    private final String nextOverdueStateName;

    @JsonCreator
    public DefaultOverdueChangeEvent(@JsonProperty("overdueObjectId") final UUID overdueObjectId,
                                     @JsonProperty("previousOverdueStateName") final String previousOverdueStateName,
                                     @JsonProperty("nextOverdueStateName") final String nextOverdueStateName,
                                     @JsonProperty("searchKey1") final Long searchKey1,
                                     @JsonProperty("searchKey2") final Long searchKey2,
                                     @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.overdueObjectId = overdueObjectId;
        this.previousOverdueStateName = previousOverdueStateName;
        this.nextOverdueStateName = nextOverdueStateName;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.OVERDUE_CHANGE;
    }

    @Override
    public String getPreviousOverdueStateName() {
        return previousOverdueStateName;
    }

    @Override
    public UUID getOverdueObjectId() {
        return overdueObjectId;
    }

    @Override
    public String getNextOverdueStateName() {
        return nextOverdueStateName;
    }

}
