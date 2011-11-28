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

import com.ning.billing.account.glue.InjectorMagic;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public abstract class EntityBase implements IEntity, IPersistable {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private static IEventBus eventBus;

    protected final UUID id;
    protected boolean isNew;

    public EntityBase(UUID id) {
        eventBus = InjectorMagic.getEventBus();
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
        IEventBusType event;

        if (isNew) {
            event = getCreateEvent();
            saveObject();
        } else {
            event = getUpdateEvent();
            updateObject();
        }

        if (event != null) {
            try {
                eventBus.post(event);
            } catch (IEventBus.EventBusException evbe) {
                log.error("Failed to post account change to event bus during save.", evbe);
            }
        }
    }

    protected abstract void saveObject();
    protected abstract void updateObject();

    protected IEventBusType getCreateEvent() {
        return null;
    }

    protected IEventBusType getUpdateEvent() {
        return null;
    }

    @Override
    public abstract void load();
}
