/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

import com.google.common.base.Objects;

public class InvoiceTrackingModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private String trackingId;
    private UUID invoiceId;
    private UUID subscriptionId;
    private String unitType;
    private LocalDate recordDate;
    private boolean isActive;

    public InvoiceTrackingModelDao() { /* For the DAO mapper */ }


    public InvoiceTrackingModelDao(final String trackingId,final UUID invoiceId, final UUID subscriptionId, final String unitType, final LocalDate recordDate) {
        this(UUIDs.randomUUID(), null, trackingId, invoiceId, subscriptionId, unitType, recordDate);
    }

    public InvoiceTrackingModelDao(final UUID id, @Nullable final DateTime createdDate, final String trackingId,
                                   final UUID invoiceId, final UUID subscriptionId, final String unitType, final LocalDate recordDate) {
        super(id, createdDate, createdDate);
        this.trackingId = trackingId;
        this.invoiceId = invoiceId;
        this.subscriptionId = subscriptionId;
        this.unitType = unitType;
        this.recordDate = recordDate;
        this.isActive = true;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(final String trackingId) {
        this.trackingId = trackingId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(final UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(final String unitType) {
        this.unitType = unitType;
    }

    // TODO required for jdbi binder
    public boolean getIsActive() {
        return isActive;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setIsActive(final boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(final LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceTrackingModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final InvoiceTrackingModelDao that = (InvoiceTrackingModelDao) o;
        return Objects.equal(trackingId, that.trackingId) &&
               Objects.equal(invoiceId, that.invoiceId) &&
               Objects.equal(isActive, that.isActive) &&
               Objects.equal(subscriptionId, that.subscriptionId) &&
               Objects.equal(unitType, that.unitType) &&
               Objects.equal(recordDate, that.recordDate);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), trackingId, invoiceId, subscriptionId, unitType, recordDate, isActive);
    }

    @Override
    public String toString() {
        return "InvoiceTrackingModelDao{" +
               "trackingId='" + trackingId + '\'' +
               ", invoiceId=" + invoiceId +
               ", subscriptionId=" + subscriptionId +
               ", unitType='" + unitType + '\'' +
               ", isActive='" + isActive + '\'' +
               ", recordDate=" + recordDate +
               '}';
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICE_TRACKING_IDS;
    }


    @Override
    public TableName getHistoryTableName() {
        return TableName.INVOICE_TRACKING_ID_HISTORY;
    }

}
