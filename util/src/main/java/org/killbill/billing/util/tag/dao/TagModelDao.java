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

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.killbill.billing.util.tag.Tag;

public class TagModelDao extends EntityModelDaoBase implements EntityModelDao<Tag> {

    private UUID tagDefinitionId;
    private UUID objectId;
    private ObjectType objectType;
    private Boolean isActive;

    public TagModelDao() { /* For the DAO mapper */ }

    public TagModelDao(final DateTime createdDate, final UUID tagDefinitionId,
                       final UUID objectId, final ObjectType objectType) {
        this(UUIDs.randomUUID(), createdDate, createdDate, tagDefinitionId, objectId, objectType);
    }

    public TagModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final UUID tagDefinitionId,
                       final UUID objectId, final ObjectType objectType) {
        super(id, createdDate, updatedDate);
        this.tagDefinitionId = tagDefinitionId;
        this.objectId = objectId;
        this.objectType = objectType;
        this.isActive = true;
    }

    public TagModelDao(final Tag tag) {
        this(tag.getId(), tag.getCreatedDate(), tag.getUpdatedDate(), tag.getTagDefinitionId(), tag.getObjectId(), tag.getObjectType());
    }

    public UUID getTagDefinitionId() {
        return tagDefinitionId;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setTagDefinitionId(final UUID tagDefinitionId) {
        this.tagDefinitionId = tagDefinitionId;
    }

    public void setObjectId(final UUID objectId) {
        this.objectId = objectId;
    }

    public void setObjectType(final ObjectType objectType) {
        this.objectType = objectType;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TagModelDao");
        sb.append("{tagDefinitionId=").append(tagDefinitionId);
        sb.append(", objectId=").append(objectId);
        sb.append(", objectType=").append(objectType);
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

        final TagModelDao that = (TagModelDao) o;

        return isSame(that);
    }

    public boolean isSame(final TagModelDao that) {
        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (tagDefinitionId != null ? !tagDefinitionId.equals(that.tagDefinitionId) : that.tagDefinitionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tagDefinitionId != null ? tagDefinitionId.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TAG;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.TAG_HISTORY;
    }

}
