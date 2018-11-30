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

package org.killbill.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Tag", parent = JsonBase.class)
public class TagJson extends JsonBase {

    private final UUID tagId;
    @ApiModelProperty(dataType = "org.killbill.billing.ObjectType")
    private final ObjectType objectType;
    private final UUID objectId;
    private final UUID tagDefinitionId;
    private final String tagDefinitionName;

    @JsonCreator
    public TagJson(@JsonProperty("tagId") final UUID tagId,
                   @JsonProperty("objectType") final ObjectType objectType,
                   @JsonProperty("objectId") final UUID objectId,
                   @JsonProperty("tagDefinitionId") final UUID tagDefinitionId,
                   @JsonProperty("tagDefinitionName") final String tagDefinitionName,
                   @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.tagId = tagId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.tagDefinitionId = tagDefinitionId;
        this.tagDefinitionName = tagDefinitionName;
    }

    public TagJson(final Tag tag, final TagDefinition tagDefinition, @Nullable final List<AuditLog> auditLogs) {
        this(tag.getId(), tag.getObjectType(), tag.getObjectId(), tagDefinition.getId(), tagDefinition.getName(), toAuditLogJson(auditLogs));
    }

    public UUID getTagId() {
        return tagId;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public UUID getTagDefinitionId() {
        return tagDefinitionId;
    }

    public String getTagDefinitionName() {
        return tagDefinitionName;
    }

    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TagJson{");
        sb.append("tagId='").append(tagId).append('\'');
        sb.append(", objectType=").append(objectType);
        sb.append(", objectId=").append(objectId);
        sb.append(", tagDefinitionId='").append(tagDefinitionId).append('\'');
        sb.append(", tagDefinitionName='").append(tagDefinitionName).append('\'');
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

        final TagJson tagJson = (TagJson) o;

        if (objectType != tagJson.objectType) {
            return false;
        }
        if (tagDefinitionId != null ? !tagDefinitionId.equals(tagJson.tagDefinitionId) : tagJson.tagDefinitionId != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(tagJson.objectId) : tagJson.objectId != null) {
            return false;
        }
        if (tagDefinitionName != null ? !tagDefinitionName.equals(tagJson.tagDefinitionName) : tagJson.tagDefinitionName != null) {
            return false;
        }
        if (tagId != null ? !tagId.equals(tagJson.tagId) : tagJson.tagId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = tagId != null ? tagId.hashCode() : 0;
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (tagDefinitionId != null ? tagDefinitionId.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (tagDefinitionName != null ? tagDefinitionName.hashCode() : 0);
        return result;
    }
}
