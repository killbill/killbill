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

package com.ning.billing.util.tag.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.TagDefinition;

public class TagDefinitionModelDao extends EntityBase implements EntityModelDao<TagDefinition> {

    private String name;
    private String description;
    private Boolean isActive;

    public TagDefinitionModelDao() { /* For the DAO mapper */ }

    public TagDefinitionModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String name, final String description) {
        super(id, createdDate, updatedDate);
        this.name = name;
        this.description = description;
        this.isActive = true;
    }

    public TagDefinitionModelDao(final ControlTagType tag) {
        this(tag.getId(), null, null, tag.name(), tag.getDescription());
    }

    public TagDefinitionModelDao(final DateTime createdDate, final String name, final String description) {
        this(UUID.randomUUID(), createdDate, createdDate, name, description);
    }

    public TagDefinitionModelDao(final TagDefinition tagDefinition) {
        this(tagDefinition.getId(), tagDefinition.getCreatedDate(), tagDefinition.getUpdatedDate(), tagDefinition.getName(),
             tagDefinition.getDescription());
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TagDefinitionModelDao");
        sb.append("{name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", isActive=").append(isActive);
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
        if (!super.equals(o)) {
            return false;
        }

        final TagDefinitionModelDao that = (TagDefinitionModelDao) o;

        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TAG_DEFINITIONS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.TAG_DEFINITION_HISTORY;
    }

}
