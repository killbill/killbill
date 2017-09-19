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

package org.killbill.billing.mock.api;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;

/**
 * Used for Jruby plugin that import util test package for default implementation of interfaces in api.
 * So despite the appearences, this class is used.
 */
public class MockExtBusEvent implements ExtBusEvent {

    private final ExtBusEventType eventType;
    private final ObjectType objectType;
    private final UUID objectId;
    private final UUID accountId;
    private final UUID tenantId;


    public MockExtBusEvent(final ExtBusEventType eventType,
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
    public String getMetaData() {
        return null;
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
    public UUID getUserToken() {
        return null;
    }

    @Override
    public String toString() {
        return "MockExtBusEvent [eventType=" + eventType + ", objectType="
               + objectType + ", objectId=" + objectId + ", accountId="
               + accountId + ", tenantId=" + tenantId + "]";
    }

}
