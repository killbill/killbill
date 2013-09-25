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

package com.ning.billing.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.util.callcontext.TenantContext;

/**
 * Internal use only
 */
public class InternalTenantContext {

    protected final Long tenantRecordId;
    protected final Long accountRecordId;

    public InternalTenantContext(final Long tenantRecordId, @Nullable final Long accountRecordId) {
        this.tenantRecordId = tenantRecordId;
        this.accountRecordId = accountRecordId;
    }

    public InternalTenantContext(final long defaultTenantRecordId) {
        this(defaultTenantRecordId, null);
    }

    public TenantContext toTenantContext(final UUID tenantId) {
        return new DefaultTenantContext(tenantId);
    }

    public Long getAccountRecordId() {
        return accountRecordId;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InternalTenantContext");
        sb.append("{accountRecordId=").append(accountRecordId);
        sb.append(", tenantRecordId=").append(tenantRecordId);
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

        final InternalTenantContext that = (InternalTenantContext) o;

        if (accountRecordId != null ? !accountRecordId.equals(that.accountRecordId) : that.accountRecordId != null) {
            return false;
        }
        if (tenantRecordId != null ? !tenantRecordId.equals(that.tenantRecordId) : that.tenantRecordId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountRecordId != null ? accountRecordId.hashCode() : 0;
        result = 31 * result + (tenantRecordId != null ? tenantRecordId.hashCode() : 0);
        return result;
    }
}
