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
package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.tag.TagDefinition;

public class TagJson extends JsonBase {

    private final String tagDefinitionId;
    private final String tagDefinitionName;

    @JsonCreator
    public TagJson(@JsonProperty("tagDefinitionId")  final String tagDefinitionId,
            @JsonProperty("tagDefinitionName") final String tagDefinitionName,
            @JsonProperty("auditLogs") @Nullable List<AuditLogJson> auditLogs) {
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
}
