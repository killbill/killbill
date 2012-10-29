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

package com.ning.billing.beatrix.extbus.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle;

public class ExtBusEventEntry implements PersistentQueueEntryLifecycle {

    private final long id;
    private final String owner;
    private final String createdOwner;
    private final DateTime nextAvailable;
    private final PersistentQueueEntryLifecycleState processingState;
    private final Long accountRecordId;
    private final Long tenantRecordId;

    private final ObjectType objectType;
    private final UUID objectId;
    private final ExtBusEventType extBusType;


    public ExtBusEventEntry(final long id, final String createdOwner, final String owner, final DateTime nextAvailable,
                         final PersistentQueueEntryLifecycleState processingState,
                         final ObjectType objectType, final UUID objectId, final  ExtBusEventType extBusType,
                         final Long accountRecordId, final Long tenantRecordId) {
        this.id = id;
        this.createdOwner = createdOwner;
        this.owner = owner;
        this.nextAvailable = nextAvailable;
        this.processingState = processingState;
        this.objectType = objectType;
        this.objectId = objectId;
        this.extBusType = extBusType;
        this.accountRecordId = accountRecordId;
        this.tenantRecordId = tenantRecordId;
    }

    public ExtBusEventEntry(final String createdOwner,
            final ObjectType objectType, final UUID objectId, final  ExtBusEventType extBusType,
                         final Long accountRecordId, final Long tenantRecordId) {
        this(0, createdOwner, null, null, null, objectType, objectId, extBusType, accountRecordId, tenantRecordId);
    }

    public long getId() {
        return id;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public ExtBusEventType getExtBusType() {
        return extBusType;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public String getCreatedOwner() {
        return createdOwner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return nextAvailable;
    }

    @Override
    public PersistentQueueEntryLifecycleState getProcessingState() {
        return processingState;
    }

    @Override
    public boolean isAvailableForProcessing(final DateTime now) {
        switch (processingState) {
            case AVAILABLE:
                break;
            case IN_PROCESSING:
                // Somebody already got the event, not available yet
                if (nextAvailable.isAfter(now)) {
                    return false;
                }
                break;
            case PROCESSED:
                return false;
            default:
                throw new RuntimeException(String.format("Unknown IEvent processing state %s", processingState));
        }
        return true;
    }

    @Override
    public Long getAccountRecordId() {
        return accountRecordId;
    }

    @Override
    public Long getTenantRecordId() {
        return tenantRecordId;
    }
}
