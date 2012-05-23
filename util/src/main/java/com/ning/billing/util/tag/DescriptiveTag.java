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

package com.ning.billing.util.tag;

import java.util.UUID;

import com.ning.billing.util.entity.EntityBase;

public class DescriptiveTag extends EntityBase implements Tag {
    private final String tagDefinitionName;

    // use to hydrate objects from the persistence layer
    public DescriptiveTag(UUID id, String tagDefinitionName) {
        super(id);
        this.tagDefinitionName = tagDefinitionName;
    }

    // use to create new objects
    public DescriptiveTag(TagDefinition tagDefinition) {
        super();
        this.tagDefinitionName = tagDefinition.getName();
    }

    // use to create new objects
    public DescriptiveTag(String tagDefinitionName) {
        super();
        this.tagDefinitionName = tagDefinitionName;
    }

    @Override
    public String getTagDefinitionName() {
        return tagDefinitionName;
    }

    @Override
    public String toString() {
        return tagDefinitionName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DescriptiveTag that = (DescriptiveTag) o;

        if (tagDefinitionName != null ? !tagDefinitionName.equals(that.tagDefinitionName) : that.tagDefinitionName != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return tagDefinitionName != null ? tagDefinitionName.hashCode() : 0;
    }
}
