/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.util.UUIDs;

public class DefaultTenant implements Tenant, Externalizable {

    private static final long serialVersionUID = -6662488328218280007L;

    private UUID id;
    private DateTime createdDate;
    private DateTime updatedDate;
    private String externalKey;
    private String apiKey;
    // Decrypted (clear) secret
    private transient String apiSecret;

    // For deserialization
    public DefaultTenant() {}

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
        this.id = id;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.externalKey = externalKey;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public DefaultTenant(final TenantModelDao tenant) {
        this(tenant.getId(), tenant.getCreatedDate(), tenant.getUpdatedDate(), tenant.getExternalKey(), tenant.getApiKey(),
             tenant.getApiSecret());
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
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
        final StringBuilder sb = new StringBuilder("DefaultTenant{");
        sb.append("id=").append(id);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", externalKey='").append(externalKey).append('\'');
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

        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (apiKey != null ? !apiKey.equals(that.apiKey) : that.apiKey != null) {
            return false;
        }
        return apiSecret != null ? apiSecret.equals(that.apiSecret) : that.apiSecret == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        result = 31 * result + (apiSecret != null ? apiSecret.hashCode() : 0);
        return result;
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        this.id = new UUID(in.readLong(), in.readLong());
        this.createdDate = new DateTime(in.readUTF());
        this.updatedDate = new DateTime(in.readUTF());
        this.externalKey = in.readBoolean() ? in.readUTF() : null;
        this.apiKey = in.readUTF();
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        oo.writeLong(id.getMostSignificantBits());
        oo.writeLong(id.getLeastSignificantBits());
        oo.writeUTF(createdDate.toString());
        oo.writeUTF(updatedDate.toString());
        oo.writeBoolean(externalKey != null);
        if (externalKey != null) {
            oo.writeUTF(externalKey);
        }
        oo.writeUTF(apiKey);
    }
}
