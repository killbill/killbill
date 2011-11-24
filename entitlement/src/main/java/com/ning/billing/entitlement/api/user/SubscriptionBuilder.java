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

package com.ning.billing.entitlement.api.user;

import java.lang.reflect.Field;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.exceptions.EntitlementError;

public class SubscriptionBuilder {

    private  UUID id;
    private  UUID bundleId;
    private  DateTime startDate;
    private  DateTime bundleStartDate;
    private  Long activeVersion;
    private  ProductCategory category;
    private  DateTime chargedThroughDate;
    private  DateTime paidThroughDate;

    public SubscriptionBuilder() {
        this.activeVersion = SubscriptionEvents.INITIAL_VERSION;
    }

    public SubscriptionBuilder(Subscription original) {
        this.id = original.getId();
        this.bundleId = original.getBundleId();
        this.startDate = original.getStartDate();
        this.bundleStartDate = original.getBundleStartDate();
        this.category = original.getCategory();
        this.activeVersion = original.getActiveVersion();
        this.chargedThroughDate = original.getChargedThroughDate();
        this.paidThroughDate = original.getPaidThroughDate();
    }

    public SubscriptionBuilder setId(UUID id) {
        this.id = id;
        return this;
    }
    public SubscriptionBuilder setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
        return this;
    }
    public SubscriptionBuilder setStartDate(DateTime startDate) {
        this.startDate = startDate;
        return this;
    }
    public SubscriptionBuilder setBundleStartDate(DateTime bundleStartDate) {
        this.bundleStartDate = bundleStartDate;
        return this;
        }
    public SubscriptionBuilder setActiveVersion(long activeVersion) {
        this.activeVersion = activeVersion;
        return this;
    }
    public SubscriptionBuilder setChargedThroughDate(DateTime chargedThroughDate) {
        this.chargedThroughDate = chargedThroughDate;
        return this;
    }
    public SubscriptionBuilder setPaidThroughDate(DateTime paidThroughDate) {
        this.paidThroughDate = paidThroughDate;
        return this;
    }
    public SubscriptionBuilder setCategory(ProductCategory category) {
        this.category = category;
        return this;
    }

    public UUID getId() {
        return id;
    }
    public UUID getBundleId() {
        return bundleId;
    }
    public DateTime getStartDate() {
        return startDate;
    }
    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }
    public Long getActiveVersion() {
        return activeVersion;
    }
    public ProductCategory getCategory() {
        return category;
    }
    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }
    public DateTime getPaidThroughDate() {
        return paidThroughDate;
    }
    private void checkAllFieldsSet() {
        for (Field cur : SubscriptionBuilder.class.getDeclaredFields()) {
            try {
                Object value = cur.get(this);
                if (value == null) {
                    throw new EntitlementError(String.format("Field %s has not been set for Subscription",
                            cur.getName()));
                }
            } catch (IllegalAccessException e) {
                throw new EntitlementError(String.format("Failed to access value for field %s for Subscription",
                        cur.getName()), e);
            }
        }
    }
}
