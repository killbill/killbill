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

package org.killbill.billing.subscription.engine.dao.model;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class SubscriptionModelDao extends EntityBase implements EntityModelDao<SubscriptionBase> {

    private UUID bundleId;
    private ProductCategory category;
    private DateTime startDate;
    private DateTime bundleStartDate;
    private long activeVersion;
    private DateTime chargedThroughDate;

    public SubscriptionModelDao() { /* For the DAO mapper */ }

    public SubscriptionModelDao(final UUID id, final UUID bundleId, final ProductCategory category, final DateTime startDate, final DateTime bundleStartDate,
                                final long activeVersion, final DateTime chargedThroughDate, final DateTime createdDate, final DateTime updateDate) {
        super(id, createdDate, updateDate);
        this.bundleId = bundleId;
        this.category = category;
        this.startDate = startDate;
        this.bundleStartDate = bundleStartDate;
        this.activeVersion = activeVersion;
        this.chargedThroughDate = chargedThroughDate;
    }

    public SubscriptionModelDao(final DefaultSubscriptionBase src) {
        this(src.getId(), src.getBundleId(), src.getCategory(), src.getAlignStartDate(), src.getBundleStartDate(), src.getActiveVersion(),
             src.getChargedThroughDate(), src.getCreatedDate(), src.getUpdatedDate());
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

    public void setBundleId(final UUID bundleId) {
        this.bundleId = bundleId;
    }

    public void setCategory(final ProductCategory category) {
        this.category = category;
    }

    public void setStartDate(final DateTime startDate) {
        this.startDate = startDate;
    }

    public void setBundleStartDate(final DateTime bundleStartDate) {
        this.bundleStartDate = bundleStartDate;
    }

    public void setActiveVersion(final long activeVersion) {
        this.activeVersion = activeVersion;
    }

    public void setChargedThroughDate(final DateTime chargedThroughDate) {
        this.chargedThroughDate = chargedThroughDate;
    }

    public static SubscriptionBase toSubscription(final SubscriptionModelDao src) {
        if (src == null) {
            return null;
        }
        return new DefaultSubscriptionBase(new SubscriptionBuilder()
                                            .setId(src.getId())
                                            .setBundleId(src.getBundleId())
                                            .setCategory(src.getCategory())
                                            .setCreatedDate(src.getCreatedDate())
                                            .setUpdatedDate(src.getUpdatedDate())
                                            .setBundleStartDate(src.getBundleStartDate())
                                            .setAlignStartDate(src.getStartDate())
                                            .setActiveVersion(src.getActiveVersion())
                                            .setChargedThroughDate(src.getChargedThroughDate())
                                            .setCreatedDate(src.getCreatedDate())
                                            .setUpdatedDate(src.getUpdatedDate()));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionModelDao");
        sb.append("{bundleId=").append(bundleId);
        sb.append(", category=").append(category);
        sb.append(", startDate=").append(startDate);
        sb.append(", bundleStartDate=").append(bundleStartDate);
        sb.append(", activeVersion=").append(activeVersion);
        sb.append(", chargedThroughDate=").append(chargedThroughDate);
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
        if (!super.equals(o)) {
            return false;
        }

        final SubscriptionModelDao that = (SubscriptionModelDao) o;

        if (activeVersion != that.activeVersion) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (bundleStartDate != null ? !bundleStartDate.equals(that.bundleStartDate) : that.bundleStartDate != null) {
            return false;
        }
        if (category != that.category) {
            return false;
        }
        if (chargedThroughDate != null ? !chargedThroughDate.equals(that.chargedThroughDate) : that.chargedThroughDate != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (bundleStartDate != null ? bundleStartDate.hashCode() : 0);
        result = 31 * result + (int) (activeVersion ^ (activeVersion >>> 32));
        result = 31 * result + (chargedThroughDate != null ? chargedThroughDate.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.SUBSCRIPTIONS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

}
