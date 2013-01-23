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

import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class TenantModelDao extends EntityBase implements EntityModelDao<Tenant> {

    private String externalKey;
    private String apiKey;
    private String apiSecret;
    private String apiSalt;

    public TenantModelDao() { /* For the DAO mapper */ }

    public TenantModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String externalKey,
                          final String apiKey, final String apiSecret, final String apiSalt) {
        super(id, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiSalt = apiSalt;
    }

    public TenantModelDao(final Tenant tenant) {
        this(tenant.getId(), tenant.getCreatedDate(), tenant.getUpdatedDate(), tenant.getExternalKey(),
             tenant.getApiKey(), tenant.getApiSecret(), null);
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getApiSalt() {
        return apiSalt;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TenantModelDao");
        sb.append("{externalKey='").append(externalKey).append('\'');
        sb.append(", apiKey='").append(apiKey).append('\'');
        sb.append(", apiSecret='").append(apiSecret).append('\'');
        sb.append(", apiSalt='").append(apiSalt).append('\'');
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

        final TenantModelDao that = (TenantModelDao) o;

        if (apiKey != null ? !apiKey.equals(that.apiKey) : that.apiKey != null) {
            return false;
        }
        if (apiSalt != null ? !apiSalt.equals(that.apiSalt) : that.apiSalt != null) {
            return false;
        }
        if (apiSecret != null ? !apiSecret.equals(that.apiSecret) : that.apiSecret != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        result = 31 * result + (apiSecret != null ? apiSecret.hashCode() : 0);
        result = 31 * result + (apiSalt != null ? apiSalt.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TENANT;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
