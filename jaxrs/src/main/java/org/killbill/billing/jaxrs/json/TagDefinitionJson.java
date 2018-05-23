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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="TagDefinition", parent = JsonBase.class)
public class TagDefinitionJson extends JsonBase {

    private final UUID id;
    private final Boolean isControlTag;
    @ApiModelProperty(required = true)
    private final String name;
    @ApiModelProperty(required = true)
    private final String description;
    private final Set<ObjectType> applicableObjectTypes;

    @JsonCreator
    public TagDefinitionJson(@JsonProperty("id") final UUID id,
                             @JsonProperty("isControlTag") final Boolean isControlTag,
                             @JsonProperty("name") final String name,
                             @JsonProperty("description") @Nullable final String description,
                             @JsonProperty("applicableObjectTypes") @Nullable final List<ObjectType> applicableObjectTypes,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.id = id;
        this.isControlTag = isControlTag;
        this.name = name;
        this.description = description;
        this.applicableObjectTypes = new HashSet<ObjectType>(applicableObjectTypes);
    }

    public TagDefinitionJson(final TagDefinition tagDefinition, @Nullable final List<AuditLog> auditLogs) {
        this(tagDefinition.getId(),
             tagDefinition.isControlTag(),
             tagDefinition.getName(),
             tagDefinition.getDescription(),
             tagDefinition.getApplicableObjectTypes(),
             toAuditLogJson(auditLogs));
    }

    public UUID getId() {
        return id;
    }

    @JsonProperty("isControlTag")
    public Boolean isControlTag() {
        return isControlTag;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<ObjectType> getApplicableObjectTypes() {
        return applicableObjectTypes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TagDefinitionJson");
        sb.append("{id='").append(id).append('\'');
        sb.append(", isControlTag=").append(isControlTag);
        sb.append(", name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", applicableObjectTypes='").append(applicableObjectTypes).append('\'');
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

        final TagDefinitionJson that = (TagDefinitionJson) o;

        if (!equalsNoId(that)) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }

        return true;
    }

    public boolean equalsNoId(final TagDefinitionJson that) {
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (isControlTag != null ? !isControlTag.equals(that.isControlTag) : that.isControlTag != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (applicableObjectTypes != null ? !applicableObjectTypes.equals(that.applicableObjectTypes) : that.applicableObjectTypes != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (isControlTag != null ? isControlTag.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (applicableObjectTypes != null ? applicableObjectTypes.hashCode() : 0);
        return result;
    }

    public static Set<ObjectType> toObjectType(final Set<String> applicableObjectTypes) {
        return ImmutableSet.copyOf(Collections2.transform(applicableObjectTypes, new Function<String, ObjectType>() {
            @Override
            public ObjectType apply(final String input) {
                return ObjectType.valueOf(input);
            }
        }));
    }
}
