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

package com.ning.billing.entitlement.events;

import java.util.UUID;

import org.joda.time.DateTime;

@SuppressWarnings("unchecked")
public class EventBaseBuilder<T extends EventBaseBuilder<T>> {

    private long totalOrdering;
    private UUID uuid;
    private UUID subscriptionId;
    private DateTime createdDate;
    private DateTime updatedDate;
    private DateTime requestedDate;
    private DateTime effectiveDate;
    private DateTime processedDate;

    private long activeVersion;
    private boolean isActive;

    public EventBaseBuilder() {
        this.uuid = UUID.randomUUID();
        this.isActive = true;
    }

    public EventBaseBuilder(final EventBaseBuilder<?> copy) {
        this.uuid = copy.uuid;
        this.subscriptionId = copy.subscriptionId;
        this.requestedDate = copy.requestedDate;
        this.effectiveDate = copy.effectiveDate;
        this.processedDate = copy.processedDate;

        this.activeVersion = copy.activeVersion;
        this.isActive = copy.isActive;
        this.totalOrdering = copy.totalOrdering;
    }

    public T setTotalOrdering(final long totalOrdering) {
        this.totalOrdering = totalOrdering;
        return (T) this;
    }

    public T setUuid(final UUID uuid) {
        this.uuid = uuid;
        return (T) this;
    }

    public T setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
        return (T) this;
    }

    public T setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
        return (T) this;
    }

    public T setSubscriptionId(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        return (T) this;
    }

    public T setRequestedDate(final DateTime requestedDate) {
        this.requestedDate = requestedDate;
        return (T) this;
    }

    public T setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
        return (T) this;
    }

    public T setProcessedDate(final DateTime processedDate) {
        this.processedDate = processedDate;
        return (T) this;
    }

    public T setActiveVersion(final long activeVersion) {
        this.activeVersion = activeVersion;
        return (T) this;
    }

    public T setActive(final boolean isActive) {
        this.isActive = isActive;
        return (T) this;
    }

    public long getTotalOrdering() {
        return totalOrdering;
    }

    public UUID getUuid() {
        return uuid;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public DateTime getProcessedDate() {
        return processedDate;
    }

    public long getActiveVersion() {
        return activeVersion;
    }

    public boolean isActive() {
        return isActive;
    }
}
