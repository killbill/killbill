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

import javax.annotation.Nullable;

import com.ning.billing.util.tag.TagDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TagDefinitionJson {

    private final String id;
    private final Boolean isControlTag;
    private final String name;
    private final String description;

    @JsonCreator
    public TagDefinitionJson(@JsonProperty("id") final String id,
                             @JsonProperty("isControlTag") final Boolean isControlTag,
                             @JsonProperty("name") final String name,
                             @JsonProperty("description") @Nullable final String description) {
        this.id = id;
        this.isControlTag = isControlTag;
        this.name = name;
        this.description = description;
    }

    public TagDefinitionJson(final TagDefinition tagDefinition) {
        this(tagDefinition.getId().toString(), tagDefinition.isControlTag(), tagDefinition.getName(), tagDefinition.getDescription());
    }

    public String getId() {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TagDefinitionJson");
        sb.append("{id='").append(id).append('\'');
        sb.append(", isControlTag=").append(isControlTag);
        sb.append(", name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
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

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (isControlTag != null ? isControlTag.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}
