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

package com.ning.billing.account.api;

import com.ning.billing.account.dao.IFieldStoreDao;
import com.ning.billing.account.glue.InjectorMagic;

import java.util.*;

public class FieldStore implements IFieldStore {
    private Map<String, ICustomField> fields = new HashMap<String, ICustomField>();
    private final UUID objectId;
    private final String objectType;

    public FieldStore(UUID objectId, String objectType) {
        this.objectId = objectId;
        this.objectType = objectType;
    }

    public static FieldStore create(UUID objectId, String objectType) {
        return new FieldStore(objectId, objectType);
    }

    public void setValue(String fieldName, String fieldValue) {
        if (fields.containsKey(fieldName)) {
            fields.get(fieldName).setValue(fieldValue);
        } else {
            fields.put(fieldName, new CustomField(fieldName, fieldValue));
        }
    }

    public String getValue(String fieldName) {
        if (fields.containsKey(fieldName)) {
            return fields.get(fieldName).getValue();
        } else {
            return null;
        }
    }

    @Override
    public List<ICustomField> getNewFields() {
        List<ICustomField> newFields = new ArrayList<ICustomField>();
        for (ICustomField field : fields.values()) {
            if (field.isNew()) {
                newFields.add(field);
            }
        }

        return newFields;
    }

    @Override
    public List<ICustomField> getUpdatedFields() {
        List<ICustomField> updatedFields = new ArrayList<ICustomField>();
        for (ICustomField field : fields.values()) {
            if (!field.isNew()) {
                updatedFields.add(field);
            }
        }

        return updatedFields;
    }

    public void save() {
        IFieldStoreDao dao = InjectorMagic.getFieldStoreDao();

        List<ICustomField> newFields = getNewFields();
        dao.createFields(objectId.toString(), objectType, newFields);
        for (ICustomField field : newFields) {
            field.setAsSaved();
        }

        dao.saveFields(objectId.toString(), objectType, getUpdatedFields());
    }

    public void load() {
        IFieldStoreDao dao = InjectorMagic.getFieldStoreDao();
        List<ICustomField> fields = dao.getFields(objectId.toString(), objectType);
        this.fields.clear();
        if (fields != null) {
            for (ICustomField field : fields) {
                this.fields.put(field.getName(), field);
            }
        }
    }
}