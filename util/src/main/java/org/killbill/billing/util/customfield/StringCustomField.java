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

package org.killbill.billing.util.customfield;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.ObjectType;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.customfield.dao.CustomFieldModelDao;

public class StringCustomField extends EntityBase implements CustomField {

    private final String fieldName;
    private final String fieldValue;
    private final UUID objectId;
    private final ObjectType objectType;

    public StringCustomField(final String name, final String value, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        this(UUIDs.randomUUID(), name, value, objectType, objectId, createdDate);
    }

    public StringCustomField(final UUID id, final String name, final String value, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        super(id, createdDate, createdDate);
        this.fieldName = name;
        this.fieldValue = value;
        this.objectId = objectId;
        this.objectType = objectType;

    }

    public StringCustomField(final CustomFieldModelDao input) {
        this(input.getId(), input.getFieldName(), input.getFieldValue(), input.getObjectType(), input.getObjectId(), input.getCreatedDate());
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getFieldValue() {
        return fieldValue;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StringCustomField");
        sb.append("{fieldName='").append(fieldName).append('\'');
        sb.append(", fieldValue='").append(fieldValue).append('\'');
        sb.append(", objectId=").append(objectId);
        sb.append(", objectType=").append(objectType);
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

        final StringCustomField that = (StringCustomField) o;

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

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
        result = 31 * result + (fieldValue != null ? fieldValue.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        return result;
    }
}
