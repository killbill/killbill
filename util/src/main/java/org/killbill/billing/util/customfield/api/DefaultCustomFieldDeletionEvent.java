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

package org.killbill.billing.util.customfield.api;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.CustomFieldDeletionEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultCustomFieldDeletionEvent extends BusEventBase implements CustomFieldDeletionEvent {

    private final UUID customFieldId;
    private final UUID objectId;
    private final ObjectType objectType;

    @JsonCreator
    public DefaultCustomFieldDeletionEvent(@JsonProperty("customFieldId") final UUID customFieldId,
                                           @JsonProperty("objectId") final UUID objectId,
                                           @JsonProperty("objectType") final ObjectType objectType,
                                           @JsonProperty("searchKey1") final Long searchKey1,
                                           @JsonProperty("searchKey2") final Long searchKey2,
                                           @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.customFieldId = customFieldId;
        this.objectId = objectId;
        this.objectType = objectType;
    }

    @Override
    public UUID getCustomFieldId() {
        return customFieldId;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.CUSTOM_FIELD_DELETION;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DefaultCustomFieldDeletionEvent{");
        sb.append("customFieldId=").append(customFieldId);
        sb.append(", objectId=").append(objectId);
        sb.append(", objectType=").append(objectType);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCustomFieldDeletionEvent)) {
            return false;
        }

        final DefaultCustomFieldDeletionEvent that = (DefaultCustomFieldDeletionEvent) o;

        if (customFieldId != null ? !customFieldId.equals(that.customFieldId) : that.customFieldId != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = customFieldId != null ? customFieldId.hashCode() : 0;
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        return result;
    }
}
