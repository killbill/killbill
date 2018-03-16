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
import org.killbill.billing.util.customfield.CustomField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="CustomField", parent = JsonBase.class)
public class CustomFieldJson extends JsonBase {

    private final UUID customFieldId;
    private final UUID objectId;
    private final ObjectType objectType;
    @ApiModelProperty(required = true)
    private final String name;
    @ApiModelProperty(required = true)
    private final String value;

    @JsonCreator
    public CustomFieldJson(@JsonProperty("customFieldId") final UUID customFieldId,
                           @JsonProperty("objectId") final UUID objectId,
                           @JsonProperty("objectType") final ObjectType objectType,
                           @JsonProperty("name") @Nullable final String name,
                           @JsonProperty("value") @Nullable final String value,
                           @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.customFieldId = customFieldId;
        this.objectId = objectId;
        this.objectType = objectType;
        this.name = name;
        this.value = value;
    }

    public CustomFieldJson(final CustomField input, @Nullable final List<AuditLog> auditLogs) {
        this(input.getId(), input.getObjectId(), input.getObjectType(), input.getFieldName(), input.getFieldValue(), toAuditLogJson(auditLogs));
    }

    public UUID getCustomFieldId() {
        return customFieldId;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CustomFieldJson{");
        sb.append("customFieldId='").append(customFieldId).append('\'');
        sb.append(", objectId=").append(objectId);
        sb.append(", objectType=").append(objectType);
        sb.append(", name='").append(name).append('\'');
        sb.append(", value='").append(value).append('\'');
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

        final CustomFieldJson that = (CustomFieldJson) o;

        if (customFieldId != null ? !customFieldId.equals(that.customFieldId) : that.customFieldId != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = customFieldId != null ? customFieldId.hashCode() : 0;
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
