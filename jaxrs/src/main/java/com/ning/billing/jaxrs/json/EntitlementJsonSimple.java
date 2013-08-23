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

package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EntitlementJsonSimple extends JsonBase {

    protected final String accountId;
    protected final String bundleId;
    protected final String subscriptionId;
    protected final String externalKey;

    @JsonCreator
    public EntitlementJsonSimple(@JsonProperty("accountId") @Nullable final String accountId,
                                 @JsonProperty("bundleId") @Nullable final String bundleId,
                                 @JsonProperty("subscriptionId") @Nullable final String entitlementId,
                                 @JsonProperty("externalKey") @Nullable final String externalKey,
                                 @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = entitlementId;
        this.externalKey = externalKey;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("EntitlementJsonSimple");
        sb.append("{subscriptionId='").append(subscriptionId).append('\'');
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

        final EntitlementJsonSimple that = (EntitlementJsonSimple) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        return result;
    }
}
