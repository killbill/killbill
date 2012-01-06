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

package com.ning.billing.util.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @Override
    public void add(T entity) {
        entities.put(getEntityKey(entity), entity);
    }

    @Override
    public void add(List<T> entities) {
        for (T entity : entities) {
            add(entity);
        }
    }

    @Override
    public void remove(T entity) {
        entities.remove(entity);
    }

    @Override
    public List<T> getEntityList() {
        return new ArrayList<T>(entities.values());
    }
}
