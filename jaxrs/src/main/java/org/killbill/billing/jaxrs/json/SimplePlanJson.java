/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="SimplePlan")
public class SimplePlanJson {

    private final String planId;
    private final String productName;
    private final ProductCategory productCategory;
    private final Currency currency;
    private final BigDecimal amount;
    private final BillingPeriod billingPeriod;
    private final Integer trialLength;
    private final TimeUnit trialTimeUnit;
    private final List<String> availableBaseProducts;

    @JsonCreator
    public SimplePlanJson(@JsonProperty("planId")  final String planId,
                          @JsonProperty("productName")  final String productName,
                          @JsonProperty("productCategory")  final ProductCategory productCategory,
                          @JsonProperty("currency")  final Currency currency,
                          @JsonProperty("amount")  final BigDecimal amount,
                          @JsonProperty("billingPeriod")  final BillingPeriod billingPeriod,
                          @JsonProperty("trialLength")  final Integer trialLength,
                          @JsonProperty("trialTimeUnit") final TimeUnit trialTimeUnit,
                          @JsonProperty("availableBaseProducts") final List<String> availableBaseProducts) {
        this.planId = planId;
        this.productName = productName;
        this.productCategory = productCategory;
        this.currency = currency;
        this.amount = amount;
        this.billingPeriod = billingPeriod;
        this.trialLength = trialLength;
        this.trialTimeUnit = trialTimeUnit;
        this.availableBaseProducts = availableBaseProducts;
    }

    public String getPlanId() {
        return planId;
    }

    public String getProductName() {
        return productName;
    }

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public Integer getTrialLength() {
        return trialLength;
    }

    public TimeUnit getTrialTimeUnit() {
        return trialTimeUnit;
    }

    public List<String> getAvailableBaseProducts() {
        return availableBaseProducts;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimplePlanJson)) {
            return false;
        }

        final SimplePlanJson that = (SimplePlanJson) o;

        if (planId != null ? !planId.equals(that.planId) : that.planId != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (trialLength != null ? !trialLength.equals(that.trialLength) : that.trialLength != null) {
            return false;
        }
        if (availableBaseProducts != null ? !availableBaseProducts.equals(that.availableBaseProducts) : that.availableBaseProducts != null) {
            return false;
        }
        return trialTimeUnit == that.trialTimeUnit;
    }

    @Override
    public int hashCode() {
        int result = planId != null ? planId.hashCode() : 0;
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (trialLength != null ? trialLength.hashCode() : 0);
        result = 31 * result + (trialTimeUnit != null ? trialTimeUnit.hashCode() : 0);
        result = 31 * result + (availableBaseProducts != null ? availableBaseProducts.hashCode() : 0);
        return result;
    }
}
