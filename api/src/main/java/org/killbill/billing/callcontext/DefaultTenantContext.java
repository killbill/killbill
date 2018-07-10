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

package org.killbill.billing.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultTenantContext implements TenantContext {

    private final UUID accountId;
    private final UUID tenantId;

    public DefaultTenantContext(@Nullable final UUID accountId, @Nullable final UUID tenantId) {
        this.accountId = accountId;
        this.tenantId = tenantId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultTenantContext");
        sb.append("{accountId=").append(accountId);
        sb.append("{tenantId=").append(tenantId);
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

        final DefaultTenantContext that = (DefaultTenantContext) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        return result;
    }
}
