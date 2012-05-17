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

package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.dao.ObjectType;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockCustomFieldDao implements CustomFieldDao {
    private final Map<UUID, List<CustomField>> fields = new HashMap<UUID, List<CustomField>>();

    @Override
    public void saveEntitiesFromTransaction(Transmogrifier transactionalDao, UUID objectId, ObjectType objectType, List<CustomField> entities, CallContext context) {
        fields.put(objectId, entities);
    }

    @Override
    public void saveEntities(UUID objectId, ObjectType objectType, List<CustomField> entities, CallContext context) {
        fields.put(objectId, entities);
    }

    @Override
    public Map<String, CustomField> loadEntities(UUID objectId, ObjectType objectType) {
        return getMap(fields.get(objectId));
    }

    @Override
    public Map<String, CustomField> loadEntitiesFromTransaction(Transmogrifier dao, UUID objectId, ObjectType objectType) {
        return getMap(fields.get(objectId));
    }

    private Map<String, CustomField> getMap(List<CustomField> customFields) {
        Map<String, CustomField> map = new HashMap<String, CustomField>();
        for (CustomField customField : customFields) {
            map.put(customField.getName(), customField);
        }
        return map;
    }
}
