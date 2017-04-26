/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.util.callcontext.TenantContext;

/**
 * Internal use only
 */
public class InternalTenantContext extends TimeAwareContext {

    protected final Long tenantRecordId;
    protected final Long accountRecordId;

    public InternalTenantContext(final Long tenantRecordId,
                                 @Nullable final Long accountRecordId,
                                 @Nullable final DateTimeZone fixedOffsetTimeZone,
                                 @Nullable final DateTime referenceDateTime) {
        super(fixedOffsetTimeZone, referenceDateTime);
        this.tenantRecordId = tenantRecordId;
        this.accountRecordId = accountRecordId;
    }

    public InternalTenantContext(final Long defaultTenantRecordId) {
        this(defaultTenantRecordId, null, null, null);
    }

    public TenantContext toTenantContext(final UUID accountId, final UUID tenantId) {
        return new DefaultTenantContext(accountId, tenantId);
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
        sb.append("{accountRecordId=").append(getAccountRecordId());
        sb.append(", tenantRecordId=").append(getTenantRecordId());
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

        if (getAccountRecordId() != null ? !getAccountRecordId().equals(that.getAccountRecordId()) : that.getAccountRecordId() != null) {
            return false;
        }
        if (getTenantRecordId() != null ? !getTenantRecordId().equals(that.getTenantRecordId()) : that.getTenantRecordId() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getAccountRecordId() != null ? getAccountRecordId().hashCode() : 0;
        result = 31 * result + (getTenantRecordId() != null ? getTenantRecordId().hashCode() : 0);
        return result;
    }
}
