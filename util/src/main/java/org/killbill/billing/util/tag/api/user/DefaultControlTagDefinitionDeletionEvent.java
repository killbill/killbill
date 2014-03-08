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

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.ControlTagDefinitionDeletionInternalEvent;
import org.killbill.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultControlTagDefinitionDeletionEvent extends BusEventBase implements ControlTagDefinitionDeletionInternalEvent {

    private final UUID tagDefinitionId;
    private final TagDefinition tagDefinition;

    @JsonCreator
    public DefaultControlTagDefinitionDeletionEvent(@JsonProperty("tagDefinitionId") final UUID tagDefinitionId,
                                                    @JsonProperty("tagDefinition") final TagDefinition tagDefinition,
                                                    @JsonProperty("searchKey1") final Long searchKey1,
                                                    @JsonProperty("searchKey2") final Long searchKey2,
                                                    @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.tagDefinitionId = tagDefinitionId;
        this.tagDefinition = tagDefinition;
    }

    @Override
    public UUID getTagDefinitionId() {
        return tagDefinitionId;
    }

    @Override
    public TagDefinition getTagDefinition() {
        return tagDefinition;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.CONTROL_TAGDEFINITION_DELETION;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultControlTagDefinitionDeletionEvent");
        sb.append("{tagDefinition=").append(tagDefinition);
        sb.append(", tagDefinitionId=").append(tagDefinitionId);
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

        final DefaultControlTagDefinitionDeletionEvent that = (DefaultControlTagDefinitionDeletionEvent) o;

        if (tagDefinition != null ? !tagDefinition.equals(that.tagDefinition) : that.tagDefinition != null) {
            return false;
        }
        if (tagDefinitionId != null ? !tagDefinitionId.equals(that.tagDefinitionId) : that.tagDefinitionId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = tagDefinitionId != null ? tagDefinitionId.hashCode() : 0;
        result = 31 * result + (tagDefinition != null ? tagDefinition.hashCode() : 0);
        return result;
    }
}
