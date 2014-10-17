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

package org.killbill.billing.jaxrs.json;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class BundleJson extends JsonBase {

    @ApiModelProperty(dataType = "java.util.UUID", required = true)
    protected final String accountId;
    @ApiModelProperty(dataType = "java.util.UUID")
    protected final String bundleId;
    protected final String externalKey;
    private final List<SubscriptionJson> subscriptions;

    @JsonCreator
    public BundleJson(@JsonProperty("accountId") @Nullable final String accountId,
                      @JsonProperty("bundleId") @Nullable final String bundleId,
                      @JsonProperty("externalKey") @Nullable final String externalKey,
                      @JsonProperty("subscriptions") @Nullable final List<SubscriptionJson> subscriptions,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.subscriptions = subscriptions;
    }

    @JsonProperty("subscriptions")
    public List<SubscriptionJson> getSubscriptions() {
        return subscriptions;
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

    public BundleJson(final SubscriptionBundle bundle, @Nullable final AccountAuditLogs accountAuditLogs) {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForBundle(bundle.getId())));
        this.accountId = bundle.getAccountId().toString();
        this.bundleId = bundle.getId().toString();
        this.externalKey = bundle.getExternalKey();

        this.subscriptions = new LinkedList<SubscriptionJson>();
        for (final Subscription cur : bundle.getSubscriptions()) {
            final ImmutableList<SubscriptionEvent> events = ImmutableList.<SubscriptionEvent>copyOf(Collections2.filter(bundle.getTimeline().getSubscriptionEvents(), new Predicate<SubscriptionEvent>() {
                @Override
                public boolean apply(@Nullable final SubscriptionEvent input) {
                    return input.getEntitlementId().equals(cur.getId());
                }
            }));
            this.subscriptions.add(new SubscriptionJson(cur, events, accountAuditLogs));
        }
    }

    @Override
    public String toString() {
        return "BundleJson{" +
               "accountId='" + accountId + '\'' +
               ", bundleId='" + bundleId + '\'' +
               ", externalKey='" + externalKey + '\'' +
               ", subscriptions=" + subscriptions +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BundleJson that = (BundleJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (subscriptions != null ? !subscriptions.equals(that.subscriptions) : that.subscriptions != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (subscriptions != null ? subscriptions.hashCode() : 0);
        return result;
    }
}
