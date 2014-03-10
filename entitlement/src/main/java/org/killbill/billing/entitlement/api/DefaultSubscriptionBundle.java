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

package org.killbill.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

public class DefaultSubscriptionBundle implements SubscriptionBundle {

    private final UUID id;
    private final UUID accountId;
    private final String externalKey;
    private final List<Subscription> subscriptions;
    private final SubscriptionBundleTimeline bundleTimeline;
    private final DateTime createdDate;
    private final DateTime updatedDate;
    private final DateTime originalCreatedDate;

    public DefaultSubscriptionBundle(final UUID id, final UUID accountId, final String externalKey, final List<Subscription> subscriptions, final SubscriptionBundleTimeline bundleTimeline,
                                     final DateTime originalCreatedDate, final DateTime createdDate, final DateTime updatedDate) {
        this.id = id;
        this.accountId = accountId;
        this.externalKey = externalKey;
        this.subscriptions = subscriptions;
        this.bundleTimeline = bundleTimeline;
        this.originalCreatedDate = originalCreatedDate;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public DateTime getOriginalCreatedDate() {
        return originalCreatedDate;
    }

    @Override
    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public SubscriptionBundleTimeline getTimeline() {
        return bundleTimeline;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }
}
