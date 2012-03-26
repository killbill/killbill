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

package com.ning.billing.util.customfield;

import java.util.List;
import java.util.UUID;
import com.ning.billing.util.entity.EntityBase;
import org.joda.time.DateTime;

public abstract class CustomizableEntityBase extends EntityBase implements Customizable {
    protected final FieldStore fields;

    public CustomizableEntityBase() {
        super();
        fields = DefaultFieldStore.create(getId(), getObjectName());
    }

    public CustomizableEntityBase(final UUID id, String createdBy, DateTime createdDate) {
        super(id, createdBy, createdDate);
        fields = DefaultFieldStore.create(getId(), getObjectName());
    }

    @Override
    public String getFieldValue(final String fieldName) {
        return fields.getValue(fieldName);
    }

    @Override
    public void setFieldValue(final String fieldName, final String fieldValue) {
        fields.setValue(fieldName, fieldValue);
    }

    @Override
    public List<CustomField> getFieldList() {
        return fields.getEntityList();
    }

    @Override
    public void setFields(final List<CustomField> fields) {
        if (fields != null) {
            this.fields.add(fields);
        }
    }

    @Override
    public void clearFields() {
        fields.clear();
    }

    public abstract String getObjectName();
}
