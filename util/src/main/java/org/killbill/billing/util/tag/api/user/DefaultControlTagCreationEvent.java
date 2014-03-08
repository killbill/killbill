/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.tag.api.user;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultControlTagCreationEvent extends BusEventBase implements ControlTagCreationInternalEvent {

    private final UUID tagId;
    private final UUID objectId;
    private final ObjectType objectType;
    private final TagDefinition tagDefinition;

    @JsonCreator
    public DefaultControlTagCreationEvent(@JsonProperty("tagId") final UUID tagId,
                                          @JsonProperty("objectId") final UUID objectId,
                                          @JsonProperty("objectType") final ObjectType objectType,
                                          @JsonProperty("tagDefinition") final TagDefinition tagDefinition,
                                          @JsonProperty("searchKey1") final Long searchKey1,
                                          @JsonProperty("searchKey2") final Long searchKey2,
                                          @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.tagId = tagId;
        this.objectId = objectId;
        this.objectType = objectType;
        this.tagDefinition = tagDefinition;
    }

    @Override
    public UUID getTagId() {
        return tagId;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public TagDefinition getTagDefinition() {
        return tagDefinition;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.CONTROL_TAG_CREATION;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultControlTagCreationEvent");
        sb.append("{objectId=").append(objectId);
        sb.append(", tagId=").append(tagId);
        sb.append(", objectType=").append(objectType);
        sb.append(", tagDefinition=").append(tagDefinition);
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

        final DefaultControlTagCreationEvent that = (DefaultControlTagCreationEvent) o;

        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (tagDefinition != null ? !tagDefinition.equals(that.tagDefinition) : that.tagDefinition != null) {
            return false;
        }
        if (tagId != null ? !tagId.equals(that.tagId) : that.tagId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = tagId != null ? tagId.hashCode() : 0;
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (tagDefinition != null ? tagDefinition.hashCode() : 0);
        return result;
    }
}
