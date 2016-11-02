/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.entity.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entity.EntityBase;

public class EntityModelDaoBase extends EntityBase {

    private Long recordId;
    private Long accountRecordId;
    private Long tenantRecordId;

    public EntityModelDaoBase(final UUID id) {
        super(id);
    }

    public EntityModelDaoBase() {
    }

    public EntityModelDaoBase(final UUID id, final DateTime createdDate, final DateTime updatedDate) {
        super(id, createdDate, updatedDate);
    }

    public EntityModelDaoBase(final EntityBase target) {
        super(target);
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public Long getAccountRecordId() {
        return accountRecordId;
    }

    public void setAccountRecordId(final Long accountRecordId) {
        this.accountRecordId = accountRecordId;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setTenantRecordId(final Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EntityModelDaoBase{");
        sb.append("recordId=").append(recordId);
        sb.append(", accountRecordId=").append(accountRecordId);
        sb.append(", tenantRecordId=").append(tenantRecordId);
        sb.append('}');
        return sb.toString();
    }
}
