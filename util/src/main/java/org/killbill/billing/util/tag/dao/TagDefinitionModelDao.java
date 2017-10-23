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

package org.killbill.billing.util.tag.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.TagDefinition;

import com.google.common.base.Joiner;

public class TagDefinitionModelDao extends EntityModelDaoBase implements EntityModelDao<TagDefinition> {

    private static final Joiner JOINER = Joiner.on(",");
    private String name;
    private String applicableObjectTypes;
    private String description;
    private Boolean isActive;

    public TagDefinitionModelDao() { /* For the DAO mapper */ }

    public TagDefinitionModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String name, final String description, String applicableObjectTypes) {
        super(id, createdDate, updatedDate);
        this.name = name;
        this.description = description;
        this.isActive = true;
        this.applicableObjectTypes = applicableObjectTypes;
    }

    public TagDefinitionModelDao(final ControlTagType tag) {
        this(tag.getId(), null, null, tag.name(), tag.getDescription(), JOINER.join(tag.getApplicableObjectTypes()));
    }

    public TagDefinitionModelDao(final DateTime createdDate, final String name, final String description, String applicableObjectTypes) {
        this(UUIDs.randomUUID(), createdDate, createdDate, name, description, applicableObjectTypes);
    }


    public TagDefinitionModelDao(final TagDefinition tagDefinition) {
        this(tagDefinition.getId(), tagDefinition.getCreatedDate(), tagDefinition.getUpdatedDate(), tagDefinition.getName(),
             tagDefinition.getDescription(), JOINER.join(tagDefinition.getApplicableObjectTypes()));
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

    public String getApplicableObjectTypes() {
        return applicableObjectTypes;
    }

    public void setApplicableObjectTypes(final String applicableObjectTypes) {
        this.applicableObjectTypes = applicableObjectTypes;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TagDefinitionModelDao");
        sb.append("{name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", applicableObjectTypes='").append(applicableObjectTypes).append('\'');
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
        if (applicableObjectTypes != null ? !applicableObjectTypes.equals(that.applicableObjectTypes) : that.applicableObjectTypes != null) {
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
        result = 31 * result + (applicableObjectTypes != null ? applicableObjectTypes.hashCode() : 0);
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
