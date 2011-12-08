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

import java.util.UUID;

public class DefaultFieldStore extends EntityCollectionBase<CustomField> implements FieldStore {
    public DefaultFieldStore(UUID objectId, String objectType) {
        super(objectId, objectType);
    }

    public static DefaultFieldStore create(UUID objectId, String objectType) {
        return new DefaultFieldStore(objectId, objectType);
    }

    @Override
    public String getEntityKey(CustomField entity) {
        return entity.getName();
    }

    public void setValue(String fieldName, String fieldValue) {
        if (entities.containsKey(fieldName)) {
            entities.get(fieldName).setValue(fieldValue);
        } else {
            entities.put(fieldName, new StringCustomField(fieldName, fieldValue));
        }
    }

    public String getValue(String fieldName) {
        if (entities.containsKey(fieldName)) {
            return entities.get(fieldName).getValue();
        } else {
            return null;
        }
    }
}