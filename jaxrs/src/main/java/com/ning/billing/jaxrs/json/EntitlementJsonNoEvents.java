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

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.Entitlement;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EntitlementJsonNoEvents extends EntitlementJsonSimple {

    private final LocalDate startDate;
    private final String productName;
    private final String productCategory;
    private final String billingPeriod;
    private final String priceList;
    private final LocalDate cancelledDate;

    @JsonCreator
    public EntitlementJsonNoEvents(@JsonProperty("accountId") @Nullable final String accountId,
                                   @JsonProperty("bundleId") @Nullable final String bundleId,
                                   @JsonProperty("entitlementId") @Nullable final String entitlementId,
                                   @JsonProperty("externalKey") @Nullable final String externalKey,
                                   @JsonProperty("startDate") @Nullable final LocalDate startDate,
                                   @JsonProperty("productName") @Nullable final String productName,
                                   @JsonProperty("productCategory") @Nullable final String productCategory,
                                   @JsonProperty("billingPeriod") @Nullable final String billingPeriod,
                                   @JsonProperty("priceList") @Nullable final String priceList,
                                   @JsonProperty("cancelledDate") @Nullable final LocalDate cancelledDate,
                                   @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(accountId, bundleId, entitlementId, externalKey, auditLogs);
        this.startDate = startDate;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceList = priceList;
        this.cancelledDate = cancelledDate;
    }

    public EntitlementJsonNoEvents(final Entitlement data, @Nullable final List<AuditLog> auditLogs) {
        this(data.getAccountId().toString(),
             data.getBundleId().toString(),
             data.getId().toString(),
             data.getExternalKey(),
             data.getEffectiveStartDate(),
             data.getProduct().getName(),
             data.getProduct().getCategory().name(),
             data.getPlan().getBillingPeriod().name(),
             data.getPriceList().getName(),
             data.getEffectiveEndDate(),
             toAuditLogJson(auditLogs));
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceList() {
        return priceList;
    }

    public LocalDate getCancelledDate() {
        return cancelledDate;
    }

    @Override
    public String toString() {
        return "SubscriptionJson [subscriptionId=" + entitlementId
               + ", productName=" + productName
               + ", productCategory=" + productCategory + ", billingPeriod="
               + billingPeriod + ", priceList=" + priceList + "]";
    }

    @Override
    public boolean equals(final Object o) {
        if (equalsNoSubscriptionIdNoStartDateNoCTD(o) && super.equals(o)) {
            final EntitlementJsonNoEvents that = (EntitlementJsonNoEvents) o;
            return ((startDate == null && that.startDate == null) || (startDate != null && that.startDate != null && startDate.compareTo(that.startDate) == 0));
        } else {
            return false;
        }
    }

    public boolean equalsNoSubscriptionIdNoStartDateNoCTD(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EntitlementJsonNoEvents that = (EntitlementJsonNoEvents) o;

        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        return result;
    }
}
