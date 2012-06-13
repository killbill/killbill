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

public class DefaultTagDefinition extends EntityBase implements TagDefinition {
    private final String name;
    private final String description;
    private final Boolean isControlTag;

    public DefaultTagDefinition(final String name, final String description, final Boolean isControlTag) {
        this(UUID.randomUUID(), name, description, isControlTag);
    }

    public DefaultTagDefinition(final UUID id, final String name, final String description, final Boolean isControlTag) {
        super(id);
        this.name = name;
        this.description = description;
        this.isControlTag = isControlTag;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Boolean isControlTag() {
        return isControlTag;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultTagDefinition");
        sb.append("{description='").append(description).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", isControlTag=").append(isControlTag);
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

        final DefaultTagDefinition that = (DefaultTagDefinition) o;

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
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (isControlTag != null ? isControlTag.hashCode() : 0);
        return result;
    }
}
