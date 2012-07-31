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

package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BundleJsonNoSubscriptions extends BundleJsonSimple {

    private final String accountId;

    @JsonCreator
    public BundleJsonNoSubscriptions(@JsonProperty("bundleId") final String bundleId,
                                     @JsonProperty("accountId") final String accountId,
                                     @JsonProperty("externalKey") final String externalKey,
                                     @JsonProperty("subscriptions") @Nullable final List<SubscriptionJsonWithEvents> subscriptions,
                                     @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(bundleId, externalKey, auditLogs);
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BundleJsonNoSubscriptions(final SubscriptionBundle bundle) {
        super(bundle.getId().toString(), bundle.getKey(), null);
        this.accountId = bundle.getAccountId().toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                 + ((bundleId == null) ? 0 : bundleId.hashCode());
        result = prime * result
                 + ((externalKey == null) ? 0 : externalKey.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!equalsNoId(obj)) {
            return false;
        }
        final BundleJsonNoSubscriptions other = (BundleJsonNoSubscriptions) obj;
        if (bundleId == null) {
            if (other.bundleId != null) {
                return false;
            }
        } else if (!bundleId.equals(other.bundleId)) {
            return false;
        }
        return true;
    }

    public boolean equalsNoId(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BundleJsonNoSubscriptions other = (BundleJsonNoSubscriptions) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (externalKey == null) {
            if (other.externalKey != null) {
                return false;
            }
        } else if (!externalKey.equals(other.externalKey)) {
            return false;
        }
        return true;
    }
}
