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

import com.ning.billing.account.dao.ITagDao;

import java.util.UUID;

public class Tag extends EntityBase {
    private ITagDao dao;

    private UUID tagDescriptionId;
    private String description;
    private UUID objectId;
    private String objectType;

    public Tag() {
        super();
    }

    public Tag(UUID id) {
        super(id);
    }

    public UUID getTagDescriptionId() {
        return tagDescriptionId;
    }

    public String getDescription() {
        return description;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public String getObjectType() {
        return objectType;
    }

    @Override
    protected void saveObject() {
        dao.create(this);
    }

    @Override
    protected void updateObject() {
        dao.update(this);
    }

    @Override
    public void load() {
        Tag that = dao.load(id);
        if (that != null) {
            this.tagDescriptionId = that.tagDescriptionId;
            this.description = that.description;
            this.objectId = that.objectId;
            this.objectType = that.objectType;
        }
    }
}
