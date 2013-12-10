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

package com.ning.billing.util.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class EntityHistoryModelDao<M extends EntityModelDao<E>, E extends Entity> extends EntityBase {

    private Long targetRecordId;
    private M entity;
    private ChangeType changeType;

    public EntityHistoryModelDao(final UUID id, final M src, final Long targetRecordId, final ChangeType type, final DateTime createdDate) {
        super(id, createdDate, createdDate);
        this.changeType = type;
        this.targetRecordId = targetRecordId;
        this.entity = src;
    }

    public EntityHistoryModelDao(final M src, final Long targetRecordId, final ChangeType type, final DateTime createdDate) {
        this(UUID.randomUUID(), src, targetRecordId, type, createdDate);
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public M getEntity() {
        return entity;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(final Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public void setEntity(final M entity) {
        this.entity = entity;
    }

    public void setChangeType(final ChangeType changeType) {
        this.changeType = changeType;
    }
}
