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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.util.clock.DefaultClock;

public class SubscriptionJsonNoEvents extends SubscriptionJsonSimple {

    private final DateTime startDate;
    
    private final String bundleId;

    private final String productName;

    private final String productCategory;

    private final String billingPeriod;

    private final String priceList;

    private final DateTime chargedThroughDate;


    @JsonCreator
    public SubscriptionJsonNoEvents(@JsonProperty("subscriptionId") String subscriptionId,
            @JsonProperty("bundleId") String bundleId,
            @JsonProperty("startDate") DateTime startDate,
            @JsonProperty("productName") String productName,
            @JsonProperty("productCategory") String productCategory,
            @JsonProperty("billingPeriod") String billingPeriod,
            @JsonProperty("priceList") String priceList,
            @JsonProperty("chargedThroughDate") DateTime chargedThroughDate) {
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
        super(null);
        this.bundleId = null;
        this.startDate = null;
        this.productName = null;
        this.productCategory = null;
        this.billingPeriod = null;
        this.priceList = null;
        this.chargedThroughDate = null;        
    }
    
    public SubscriptionJsonNoEvents(final Subscription data) {
        super(data.getId().toString());
        this.bundleId = data.getBundleId().toString();
        this.startDate = data.getStartDate();
        this.productName = data.getCurrentPlan().getProduct().getName();
        this.productCategory = data.getCurrentPlan().getProduct().getCategory().toString();
        this.billingPeriod = data.getCurrentPlan().getBillingPeriod().toString();
        this.priceList = data.getCurrentPriceList().getName();
        this.chargedThroughDate = data.getChargedThroughDate();
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((billingPeriod == null) ? 0 : billingPeriod.hashCode());
        result = prime * result
                + ((bundleId == null) ? 0 : bundleId.hashCode());
        result = prime * result
                + ((priceList == null) ? 0 : priceList.hashCode());
        result = prime * result
                + ((productCategory == null) ? 0 : productCategory.hashCode());
        result = prime * result
                + ((productName == null) ? 0 : productName.hashCode());
        result = prime * result
                + ((subscriptionId == null) ? 0 : subscriptionId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (equalsNoId(obj) == false) {
            return false;
        }
        SubscriptionJsonNoEvents other = (SubscriptionJsonNoEvents) obj;
        if (subscriptionId == null) {
            if (other.subscriptionId != null)
                return false;
        } else if (!subscriptionId.equals(other.subscriptionId))
            return false;
        return true;
    }

    public boolean equalsNoId(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SubscriptionJsonNoEvents other = (SubscriptionJsonNoEvents) obj;
        if (billingPeriod == null) {
            if (other.billingPeriod != null)
                return false;
        } else if (!billingPeriod.equals(other.billingPeriod))
            return false;
        if (bundleId == null) {
            if (other.bundleId != null)
                return false;
        } else if (!bundleId.equals(other.bundleId))
            return false;
        if (priceList == null) {
            if (other.priceList != null)
                return false;
        } else if (!priceList.equals(other.priceList))
            return false;
        if (productCategory == null) {
            if (other.productCategory != null)
                return false;
        } else if (!productCategory.equals(other.productCategory))
            return false;
        if (productName == null) {
            if (other.productName != null)
                return false;
        } else if (!productName.equals(other.productName))
            return false;
        return true;
    }
}
