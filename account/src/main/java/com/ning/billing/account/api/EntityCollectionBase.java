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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class EntityCollectionBase<T extends Entity> implements EntityCollection<T> {
    protected Map<String, T> entities = new HashMap<String, T>();
    protected final UUID objectId;
    protected final String objectType;

    public EntityCollectionBase(UUID objectId, String objectType) {
        this.objectId = objectId;
        this.objectType = objectType;
    }

    @Override
    public void clear() {
        entities.clear();
    }

    @Override
    public abstract String getEntityKey(T entity);

//    public void save() {
//        IEntityCollectionDao<T> dao = getCollectionDao();
//
//        dao.save(objectId.toString(), objectType, new ArrayList(entities.values()));
//    }
//
//    public void load() {
//        IEntityCollectionDao<T> dao = getCollectionDao();
//
//        List<T> entities = dao.load(objectId.toString(), objectType);
//        this.entities.clear();
//        if (entities != null) {
//            for (T entity : entities) {
//                this.entities.put(getEntityKey(entity), entity);
//            }
//        }
//    }
}
