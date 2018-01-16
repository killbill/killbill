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

package org.killbill.billing.util.customfield.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.killbill.billing.util.tag.dao.TagModelDao;

public class CustomFieldModelDao extends EntityModelDaoBase implements EntityModelDao<CustomField> {

    private String fieldName;
    private String fieldValue;
    private UUID objectId;
    private ObjectType objectType;
    private Boolean isActive;

    public CustomFieldModelDao() {  /* For the DAO mapper */ }

    public CustomFieldModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String fieldName,
                               final String fieldValue, final UUID objectId, final ObjectType objectType) {
        super(id, createdDate, updatedDate);
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.objectId = objectId;
        this.objectType = objectType;
        this.isActive = true;
    }

    public CustomFieldModelDao(final DateTime createdDate, final String fieldName, final String fieldValue, final UUID objectId, final ObjectType objectType) {
        this(UUIDs.randomUUID(), createdDate, createdDate, fieldName, fieldValue, objectId, objectType);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
    }

    public void setFieldValue(final String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public void setObjectId(final UUID objectId) {
        this.objectId = objectId;
    }

    public void setObjectType(final ObjectType objectType) {
        this.objectType = objectType;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CustomFieldModelDao");
        sb.append("{fieldName='").append(fieldName).append('\'');
        sb.append(", fieldValue='").append(fieldValue).append('\'');
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

        final CustomFieldModelDao that = (CustomFieldModelDao) o;

        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) {
            return false;
        }
        if (fieldValue != null ? !fieldValue.equals(that.fieldValue) : that.fieldValue != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }

        return true;
    }

    public boolean isSame(final CustomFieldModelDao that) {
        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) {
            return false;
        }
        if (fieldValue != null ? !fieldValue.equals(that.fieldValue) : that.fieldValue != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }
        return true;
    }



    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
        result = 31 * result + (fieldValue != null ? fieldValue.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.CUSTOM_FIELD;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.CUSTOM_FIELD_HISTORY;
    }

}
