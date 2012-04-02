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

import org.joda.time.DateTime;

import java.util.UUID;

public abstract class EntityBase implements Entity {
    protected final UUID id;
    protected final String createdBy;
    protected final DateTime createdDate;

    // used to hydrate objects
    public EntityBase(UUID id, String createdBy, DateTime createdDate) {
        this.id = id;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
    }

    // used to create new objects
    public EntityBase() {
        this.id = UUID.randomUUID();
        this.createdBy = null;
        this.createdDate = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }
}