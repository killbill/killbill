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

package com.ning.billing.entitlement.engine.dao.model;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.util.entity.EntityBase;

public class SubscriptionModelDao extends EntityBase {

    private final UUID bundleId;
    private final ProductCategory category;
    private final DateTime startDate;
    private final DateTime bundleStartDate;
    private final long activeVersion;
    private final DateTime chargedThroughDate;
    private final DateTime paidThroughDate;

    public SubscriptionModelDao(final UUID id, final UUID bundleId, final ProductCategory category, final DateTime startDate, final DateTime bundleStartDate,
                                final long activeVersion, final DateTime chargedThroughDate, final DateTime paidThroughDate, final DateTime createdDate, final DateTime updateDate) {
        super(id, createdDate, updateDate);
        this.bundleId = bundleId;
        this.category = category;
        this.startDate = startDate;
        this.bundleStartDate = bundleStartDate;
        this.activeVersion = activeVersion;
        this.chargedThroughDate = chargedThroughDate;
        this.paidThroughDate = paidThroughDate;
    }

    public SubscriptionModelDao(final SubscriptionData src) {
        this(src.getId(), src.getBundleId(), src.getCategory(), src.getAlignStartDate(), src.getBundleStartDate(), src.getActiveVersion(),
             src.getChargedThroughDate(), src.getPaidThroughDate(), src.getCreatedDate(), src.getUpdatedDate());
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }

    public long getActiveVersion() {
        return activeVersion;
    }

    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    public DateTime getPaidThroughDate() {
        return paidThroughDate;
    }

    public static Subscription toSubscription(SubscriptionModelDao src) {
        if (src == null) {
            return null;
        }
        return new SubscriptionData(new SubscriptionBuilder()
                                            .setId(src.getId())
                                            .setBundleId(src.getBundleId())
                                            .setCategory(src.getCategory())
                                            .setCreatedDate(src.getCreatedDate())
                                            .setUpdatedDate(src.getUpdatedDate())
                                            .setBundleStartDate(src.getBundleStartDate())
                                            .setAlignStartDate(src.getStartDate())
                                            .setActiveVersion(src.getActiveVersion())
                                            .setChargedThroughDate(src.getChargedThroughDate())
                                            .setPaidThroughDate(src.getPaidThroughDate())
                                            .setCreatedDate(src.getCreatedDate())
                                            .setUpdatedDate(src.getUpdatedDate()));
    }
}
