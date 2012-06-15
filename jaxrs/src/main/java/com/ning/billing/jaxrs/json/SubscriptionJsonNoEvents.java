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

package com.ning.billing.jaxrs.json;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.entitlement.api.user.Subscription;

public class SubscriptionJsonNoEvents extends SubscriptionJsonSimple {
    private final DateTime startDate;

    private final String bundleId;

    private final String productName;

    private final String productCategory;

    private final String billingPeriod;

    private final String priceList;

    private final DateTime chargedThroughDate;

    @JsonCreator
    public SubscriptionJsonNoEvents(@JsonProperty("subscriptionId") @Nullable final String subscriptionId,
                                    @JsonProperty("bundleId") @Nullable final String bundleId,
                                    @JsonProperty("startDate") @Nullable final DateTime startDate,
                                    @JsonProperty("productName") @Nullable final String productName,
                                    @JsonProperty("productCategory") @Nullable final String productCategory,
                                    @JsonProperty("billingPeriod") @Nullable final String billingPeriod,
                                    @JsonProperty("priceList") @Nullable final String priceList,
                                    @JsonProperty("chargedThroughDate") @Nullable final DateTime chargedThroughDate) {
        super(subscriptionId);
        this.bundleId = bundleId;
        this.startDate = startDate;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceList = priceList;
        this.chargedThroughDate = chargedThroughDate;
    }

    public SubscriptionJsonNoEvents() {
        this(null, null, null, null, null, null, null, null);
    }

    public SubscriptionJsonNoEvents(final Subscription data) {
        this(data.getId().toString(), data.getBundleId().toString(), data.getStartDate(), data.getCurrentPlan().getProduct().getName(),
             data.getCurrentPlan().getProduct().getCategory().toString(), data.getCurrentPlan().getBillingPeriod().toString(),
             data.getCurrentPriceList().getName(), data.getChargedThroughDate());
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public DateTime getStartDate() {
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

    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    @Override
    public String toString() {
        return "SubscriptionJson [subscriptionId=" + subscriptionId
                + ", bundleId=" + bundleId + ", productName=" + productName
                + ", productCategory=" + productCategory + ", billingPeriod="
                + billingPeriod + ", priceList=" + priceList + "]";
    }

    @Override
    public boolean equals(final Object o) {
        if (equalsNoSubscriptionIdNoStartDateNoCTD(o) && super.equals(o)) {
            final SubscriptionJsonNoEvents that = (SubscriptionJsonNoEvents) o;
            return ((startDate == null && that.startDate == null) || (startDate != null && that.startDate != null && startDate.compareTo(that.startDate) == 0)) &&
                    ((chargedThroughDate == null && that.chargedThroughDate == null) || (chargedThroughDate != null && that.chargedThroughDate != null &&
                            chargedThroughDate.compareTo(that.chargedThroughDate) == 0));
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

        final SubscriptionJsonNoEvents that = (SubscriptionJsonNoEvents) o;

        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
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
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (chargedThroughDate != null ? chargedThroughDate.hashCode() : 0);
        return result;
    }
}
