/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.tenant.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.tenant.api.TenantKV;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class TenantKVModelDao extends EntityBase implements EntityModelDao<TenantKV> {

    private String tenantKey;
    private String tenantValue;

    private Boolean isActive;

    public TenantKVModelDao() { /* For the DAO mapper */ }

    public TenantKVModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String key, final String value) {
        super(id, createdDate, updatedDate);
        this.tenantKey = key;
        this.tenantValue = value;
        this.isActive = true;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getTenantValue() {
        return tenantValue;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TenantKVModelDao");
        sb.append("{key='").append(tenantKey).append('\'');
        sb.append(", value='").append(tenantValue).append('\'');
        sb.append(", isActive=").append(isActive);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final TenantKVModelDao that = (TenantKVModelDao) o;

        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }
        if (tenantKey != null ? !tenantKey.equals(that.tenantKey) : that.tenantKey != null) {
            return false;
        }
        if (tenantValue != null ? !tenantValue.equals(that.tenantValue) : that.tenantValue != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tenantKey != null ? tenantKey.hashCode() : 0);
        result = 31 * result + (tenantValue != null ? tenantValue.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TENANT_KVS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
