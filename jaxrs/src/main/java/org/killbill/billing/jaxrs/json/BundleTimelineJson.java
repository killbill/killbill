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
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.entitlement.api.SubscriptionBundleTimeline;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.jaxrs.json.SubscriptionJson.EventSubscriptionJson;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="BundleTimeline", parent = JsonBase.class)
public class BundleTimelineJson extends JsonBase {

    private final UUID accountId;
    private final UUID bundleId;
    private final String externalKey;
    private final List<EventSubscriptionJson> events;

    @JsonCreator
    public BundleTimelineJson(@JsonProperty("accountId") @Nullable final UUID accountId,
                              @JsonProperty("bundleId") @Nullable final UUID bundleId,
                              @JsonProperty("externalKey") @Nullable final String externalKey,
                              @JsonProperty("events") @Nullable final List<EventSubscriptionJson> events,
                              @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.events = events;
    }

    public BundleTimelineJson(final SubscriptionBundleTimeline bundleTimeline, @Nullable final AccountAuditLogs accountAuditLogs) {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForBundle(bundleTimeline.getBundleId())));
        this.accountId = bundleTimeline.getAccountId();
        this.bundleId = bundleTimeline.getBundleId();
        this.externalKey = bundleTimeline.getExternalKey();

        this.events = new LinkedList<EventSubscriptionJson>();
        for (final SubscriptionEvent subscriptionEvent : bundleTimeline.getSubscriptionEvents()) {
            this.events.add(new EventSubscriptionJson(subscriptionEvent, accountAuditLogs));
        }
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

    public List<EventSubscriptionJson> getEvents() {
        return events;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BundleTimelineJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", bundleId='").append(bundleId).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", events=").append(events);
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

        final BundleTimelineJson that = (BundleTimelineJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (events != null ? events.hashCode() : 0);
        return result;
    }
}
