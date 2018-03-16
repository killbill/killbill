/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Bundle", parent = JsonBase.class)
public class BundleJson extends JsonBase {

    @ApiModelProperty(required = true)
    private final UUID accountId;
    private final UUID bundleId;
    private final String externalKey;
    private final List<SubscriptionJson> subscriptions;
    private final BundleTimelineJson timeline;

    @JsonCreator
    public BundleJson(@JsonProperty("accountId") @Nullable final UUID accountId,
                      @JsonProperty("bundleId") @Nullable final UUID bundleId,
                      @JsonProperty("externalKey") @Nullable final String externalKey,
                      @JsonProperty("subscriptions") @Nullable final List<SubscriptionJson> subscriptions,
                      @JsonProperty("timeline") @Nullable final BundleTimelineJson timeline,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.subscriptions = subscriptions;
        this.timeline = timeline;
    }

    public BundleJson(final SubscriptionBundle bundle, @Nullable final Currency currency, @Nullable final AccountAuditLogs accountAuditLogs) throws CatalogApiException {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForBundle(bundle.getId())));
        this.accountId = bundle.getAccountId();
        this.bundleId = bundle.getId();
        this.externalKey = bundle.getExternalKey();
        this.subscriptions = new LinkedList<SubscriptionJson>();
        for (final Subscription subscription : bundle.getSubscriptions()) {
            this.subscriptions.add(new SubscriptionJson(subscription, currency, accountAuditLogs));
        }
        this.timeline = new BundleTimelineJson(bundle.getTimeline(), accountAuditLogs);
    }



    public List<SubscriptionJson> getSubscriptions() {
        return subscriptions;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public BundleTimelineJson getTimeline() {
        return timeline;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BundleJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", bundleId='").append(bundleId).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", subscriptions=").append(subscriptions);
        sb.append(", timeline=").append(timeline);
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
        if (timeline != null ? !timeline.equals(that.timeline) : that.timeline != null) {
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
        result = 31 * result + (timeline != null ? timeline.hashCode() : 0);
        return result;
    }
}
