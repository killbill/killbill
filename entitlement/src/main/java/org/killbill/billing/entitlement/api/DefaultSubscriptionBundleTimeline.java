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

package org.killbill.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalTenantContext;

public class DefaultSubscriptionBundleTimeline implements SubscriptionBundleTimeline {

    private final UUID accountId;
    private final UUID bundleId;
    private final String externalKey;
    private final List<SubscriptionEvent> events;

    public DefaultSubscriptionBundleTimeline(final UUID accountId,
                                             final UUID bundleId,
                                             final String externalKey,
                                             final Iterable<Entitlement> entitlements,
                                             final InternalTenantContext internalTenantContext) {
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.events = SubscriptionEventOrdering.sortedCopy(entitlements, internalTenantContext);
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public List<SubscriptionEvent> getSubscriptionEvents() {
        return events;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultSubscriptionBundleTimeline{");
        sb.append("accountId=").append(accountId);
        sb.append(", bundleId=").append(bundleId);
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

        final DefaultSubscriptionBundleTimeline that = (DefaultSubscriptionBundleTimeline) o;

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
