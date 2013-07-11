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

package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TagJson extends JsonBase {

    private final String tagDefinitionId;
    private final String tagDefinitionName;

    @JsonCreator
    public TagJson(@JsonProperty("tagDefinitionId") final String tagDefinitionId,
                   @JsonProperty("tagDefinitionName") final String tagDefinitionName,
                   @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.tagDefinitionId = tagDefinitionId;
        this.tagDefinitionName = tagDefinitionName;
    }

    public TagJson(final TagDefinition tagDefintion, @Nullable final List<AuditLog> auditLogs) {
        this(tagDefintion.getId().toString(), tagDefintion.getName(), toAuditLogJson(auditLogs));
    }

    public String getTagDefinitionId() {
        return tagDefinitionId;
    }

    public String getTagDefinitionName() {
        return tagDefinitionName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TagJson{");
        sb.append("tagDefinitionId='").append(tagDefinitionId).append('\'');
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

        if (tagDefinitionId != null ? !tagDefinitionId.equals(tagJson.tagDefinitionId) : tagJson.tagDefinitionId != null) {
            return false;
        }
        if (tagDefinitionName != null ? !tagDefinitionName.equals(tagJson.tagDefinitionName) : tagJson.tagDefinitionName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = tagDefinitionId != null ? tagDefinitionId.hashCode() : 0;
        result = 31 * result + (tagDefinitionName != null ? tagDefinitionName.hashCode() : 0);
        return result;
    }
}
