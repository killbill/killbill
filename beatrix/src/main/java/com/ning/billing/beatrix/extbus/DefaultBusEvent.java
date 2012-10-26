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
package com.ning.billing.beatrix.extbus;

import java.util.UUID;

import com.ning.billing.beatrix.bus.api.BusEvent;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.util.dao.ObjectType;

public class DefaultBusEvent implements BusEvent {

    private final ExtBusEventType eventType;
    private final ObjectType objectType;
    private final UUID objectId;
    private final UUID accountId;
    private final UUID tenantId;


    public DefaultBusEvent(final ExtBusEventType eventType,
            final ObjectType objectType,
            final UUID objectId,
            final UUID accountId,
            final UUID tenantId) {
        this.eventType = eventType;
        this.objectId = objectId;
        this.objectType = objectType;
        this.accountId = accountId;
        this.tenantId = tenantId;
    }

    @Override
    public ExtBusEventType getEventType() {
        return eventType;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "DefaultBusEvent [eventType=" + eventType + ", objectType="
                + objectType + ", objectId=" + objectId + ", accountId="
                + accountId + ", tenantId=" + tenantId + "]";
    }



}
