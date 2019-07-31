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

package org.killbill.billing.subscription.api.user;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.ProductCategory;

public class SubscriptionBuilder {

    private UUID id;
    private UUID bundleId;
    private String externalKey;
    private String bundleExternalKey;
    private DateTime createdDate;
    private DateTime updatedDate;
    private DateTime alignStartDate;
    private DateTime bundleStartDate;
    private ProductCategory category;
    private DateTime chargedThroughDate;
    private boolean migrated;
    private Integer subscriptionBCD;

    public SubscriptionBuilder() {
    }

    public SubscriptionBuilder(final DefaultSubscriptionBase original) {
        this.id = original.getId();
        this.bundleId = original.getBundleId();
        this.externalKey = original.getExternalKey();
        this.bundleExternalKey = original.getBundleExternalKey();
        this.alignStartDate = original.getAlignStartDate();
        this.bundleStartDate = original.getBundleStartDate();
        this.category = original.getCategory();
        this.chargedThroughDate = original.getChargedThroughDate();
        this.migrated = original.isMigrated();
    }

    public UUID getId() {
        return id;
    }

    public SubscriptionBuilder setId(final UUID id) {
        this.id = id;
        return this;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public SubscriptionBuilder setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public SubscriptionBuilder setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
        return this;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public SubscriptionBuilder setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
        return this;
    }

    public SubscriptionBuilder setBundleId(final UUID bundleId) {
        this.bundleId = bundleId;
        return this;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public SubscriptionBuilder setBundleExternalKey(final String bundleExternalKey) {
        this.bundleExternalKey = bundleExternalKey;
        return this;
    }


    public DateTime getAlignStartDate() {
        return alignStartDate;
    }

    public SubscriptionBuilder setAlignStartDate(final DateTime alignStartDate) {
        this.alignStartDate = alignStartDate;
        return this;
    }

    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }

    public SubscriptionBuilder setBundleStartDate(final DateTime bundleStartDate) {
        this.bundleStartDate = bundleStartDate;
        return this;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public SubscriptionBuilder setCategory(final ProductCategory category) {
        this.category = category;
        return this;
    }

    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    public SubscriptionBuilder setChargedThroughDate(final DateTime chargedThroughDate) {
        this.chargedThroughDate = chargedThroughDate;
        return this;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public SubscriptionBuilder setMigrated(final boolean migrated) {
        this.migrated = migrated;
        return this;
    }

    public Integer getSubscriptionBCD() {
        return subscriptionBCD;
    }

    public SubscriptionBuilder setSubscriptionBCD(final Integer subscriptionBCD) {
        this.subscriptionBCD = subscriptionBCD;
        return this;
    }

}
