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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.entitlement.api.SubscriptionBundle;
import com.ning.billing.entitlement.api.SubscriptionBundleTimeline.SubscriptionEvent;
import com.ning.billing.subscription.api.timeline.BundleBaseTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class BundleJsonWithSubscriptions extends BundleJsonSimple {

    private final List<EntitlementJsonWithEvents> subscriptions;

    @JsonCreator
    public BundleJsonWithSubscriptions(@JsonProperty("bundleId") @Nullable final String bundleId,
                                       @JsonProperty("externalKey") @Nullable final String externalKey,
                                       @JsonProperty("subscriptions") @Nullable final List<EntitlementJsonWithEvents> subscriptions,
                                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(bundleId, externalKey, auditLogs);
        this.subscriptions = subscriptions;
    }

    @JsonProperty("subscriptions")
    public List<EntitlementJsonWithEvents> getSubscriptions() {
        return subscriptions;
    }

    public BundleJsonWithSubscriptions(final SubscriptionBundle bundle, final List<AuditLog> auditLogs,
                                       final Map<UUID, List<AuditLog>> subscriptionsAuditLogs, final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs) {
        super(bundle.getId(), bundle.getExternalKey(), auditLogs);
        this.subscriptions = new LinkedList<EntitlementJsonWithEvents>();
        for (final Subscription cur : bundle.getSubscriptions()) {

            final ImmutableList<SubscriptionEvent> events =  ImmutableList.<SubscriptionEvent>copyOf(Collections2.filter(bundle.getTimeline().getSubscriptionEvents(), new Predicate<SubscriptionEvent>() {
                @Override
                public boolean apply(@Nullable final SubscriptionEvent input) {
                    return input.getEntitlementId().equals(cur.getId());
                }
            }));
            this.subscriptions.add(new EntitlementJsonWithEvents(cur,
                                                                 events,
                                                                 subscriptionsAuditLogs.get(cur.getId()), subscriptionEventsAuditLogs));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final BundleJsonWithSubscriptions that = (BundleJsonWithSubscriptions) o;

        if (subscriptions != null ? !subscriptions.equals(that.subscriptions) : that.subscriptions != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subscriptions != null ? subscriptions.hashCode() : 0);
        return result;
    }
}
