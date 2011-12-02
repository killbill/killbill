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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FieldStore extends EntityCollectionBase<ICustomField> implements IFieldStore {
    public FieldStore(UUID objectId, String objectType) {
        super(objectId, objectType);
    }

    public static FieldStore create(UUID objectId, String objectType) {
        return new FieldStore(objectId, objectType);
    }

    @Override
    public String getEntityKey(ICustomField entity) {
        return entity.getName();
    }

    public void setValue(String fieldName, String fieldValue) {
        if (entities.containsKey(fieldName)) {
            entities.get(fieldName).setValue(fieldValue);
        } else {
            entities.put(fieldName, new CustomField(fieldName, fieldValue));
        }
    }

    public String getValue(String fieldName) {
        if (entities.containsKey(fieldName)) {
            return entities.get(fieldName).getValue();
        } else {
            return null;
        }
    }

    @Override
    public List<ICustomField> getFieldList() {
        return new ArrayList<ICustomField>(entities.values());
    }

    @Override
    public void add(ICustomField field) {
        entities.put(field.getName(), field);
    }

    @Override
    public void add(List<ICustomField> fields) {
        for (ICustomField field : fields) {
            add(field);
        }
    }
}