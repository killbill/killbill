/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.tenant.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;

public class DefaultTenant extends EntityBase implements Tenant {

    private final String externalKey;
    private final String apiKey;
    // Decrypted (clear) secret
    private final String apiSecret;

    /**
     * This call is used to create a tenant
     *
     * @param data TenantData new data for the tenant
     */
    public DefaultTenant(final TenantData data) {
        this(UUIDs.randomUUID(), data);
    }

    /**
     * This call is used to update an existing tenant
     *
     * @param id   UUID id of the existing tenant to update
     * @param data TenantData new data for the existing tenant
     */
    public DefaultTenant(final UUID id, final TenantData data) {
        this(id, null, null, data.getExternalKey(), data.getApiKey(), data.getApiSecret());
    }

    public DefaultTenant(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                         final String externalKey, final String apiKey, final String apiSecret) {
        super(id, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public DefaultTenant(final TenantModelDao tenant) {
        this(tenant.getId(), tenant.getCreatedDate(), tenant.getUpdatedDate(), tenant.getExternalKey(), tenant.getApiKey(),
             tenant.getApiSecret());
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String getApiSecret() {
        return apiSecret;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultTenant");
        sb.append("{externalKey='").append(externalKey).append('\'');
        sb.append(", apiKey='").append(apiKey).append('\'');
        // Don't print the secret
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

        final DefaultTenant that = (DefaultTenant) o;

        if (apiKey != null ? !apiKey.equals(that.apiKey) : that.apiKey != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (apiSecret != null ? !apiSecret.equals(that.apiSecret) : that.apiSecret != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = externalKey != null ? externalKey.hashCode() : 0;
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        result = 31 * result + (apiSecret != null ? apiSecret.hashCode() : 0);
        return result;
    }
}
