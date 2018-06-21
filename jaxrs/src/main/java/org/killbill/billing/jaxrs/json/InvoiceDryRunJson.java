/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="InvoiceDryRun")
public class InvoiceDryRunJson {

    private final DryRunType dryRunType;
    private final SubscriptionEventType dryRunAction;
    private final PhaseType phaseType;
    private final String productName;
    private final ProductCategory productCategory;
    private final BillingPeriod billingPeriod;
    private final String priceListName;
    private final LocalDate effectiveDate;
    private final UUID subscriptionId;
    private final UUID bundleId;
    private final BillingActionPolicy billingPolicy;
    private final List<PhasePriceJson> priceOverrides;

    @JsonCreator
    public InvoiceDryRunJson(@JsonProperty("dryRunType") @Nullable final DryRunType dryRunType,
                             @JsonProperty("dryRunAction") @Nullable final SubscriptionEventType dryRunAction,
                             @JsonProperty("phaseType") @Nullable final PhaseType phaseType,
                             @JsonProperty("productName") @Nullable final String productName,
                             @JsonProperty("productCategory") @Nullable final ProductCategory productCategory,
                             @JsonProperty("billingPeriod") @Nullable final BillingPeriod billingPeriod,
                             @JsonProperty("priceListName") @Nullable final String priceListName,
                             @JsonProperty("subscriptionId") @Nullable final UUID subscriptionId,
                             @JsonProperty("bundleId") @Nullable final UUID bundleId,
                             @JsonProperty("effectiveDate") @Nullable final LocalDate effectiveDate,
                             @JsonProperty("billingPolicy") @Nullable final BillingActionPolicy billingPolicy,
                             @JsonProperty("priceOverrides") @Nullable final List<PhasePriceJson> priceOverrides) {
        this.dryRunType = dryRunType;
        this.dryRunAction = dryRunAction;
        this.phaseType = phaseType;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceListName = priceListName;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.effectiveDate = effectiveDate;
        this.billingPolicy = billingPolicy;
        this.priceOverrides = priceOverrides;
    }

    public DryRunType getDryRunType() {
        return dryRunType;
    }

    public SubscriptionEventType getDryRunAction() {
        return dryRunAction;
    }

    public PhaseType getPhaseType() {
        return phaseType;
    }

    public String getProductName() {
        return productName;
    }

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceListName() {
        return priceListName;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public BillingActionPolicy getBillingPolicy() {
        return billingPolicy;
    }

    public List<PhasePriceJson> getPriceOverrides() {
        return priceOverrides;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceDryRunJson)) {
            return false;
        }

        final InvoiceDryRunJson that = (InvoiceDryRunJson) o;

        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (billingPolicy != null ? !billingPolicy.equals(that.billingPolicy) : that.billingPolicy != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (dryRunType != null ? !dryRunType.equals(that.dryRunType) : that.dryRunType != null) {
            return false;
        }
        if (dryRunAction != null ? !dryRunAction.equals(that.dryRunAction) : that.dryRunAction != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (phaseType != null ? !phaseType.equals(that.phaseType) : that.phaseType != null) {
            return false;
        }
        if (priceListName != null ? !priceListName.equals(that.priceListName) : that.priceListName != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (priceOverrides != null ? !priceOverrides.equals(that.priceOverrides) : that.priceOverrides != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = dryRunAction != null ? dryRunAction.hashCode() : 0;
        result = 31 * result + (dryRunType != null ? dryRunType.hashCode() : 0);
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceListName != null ? priceListName.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (billingPolicy != null ? billingPolicy.hashCode() : 0);
        result = 31 * result + (priceOverrides != null ? priceOverrides.hashCode() : 0);
        return result;
    }
}
