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

public abstract class EntityBase implements IEntity, IPersistable {
    protected final UUID id;
    protected boolean isNew;

    public EntityBase(UUID id) {
        this.id = id;
        this.isNew = false;
    }

    public EntityBase() {
        this(UUID.randomUUID());
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getIdAsString() {
        return id.toString();
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @Override
    public void setAsSaved() {
        this.isNew = false;
    }

    @Override
    public void save() {
        if (isNew) {
            saveObject();
        } else {
            updateObject();
        }
    }

    protected abstract void saveObject();
    protected abstract void updateObject();

    @Override
    public abstract void load();
}
