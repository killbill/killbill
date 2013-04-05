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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.osgi.bundles.analytics.utils.Rounder;

import static com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

/**
 * Describe a subscription for Analytics purposes
 */
public class BusinessSubscription {

    private static final Currency USD = Currency.valueOf("USD");

    private final String productName;
    private final String productType;
    private final String productCategory;
    private final String slug;
    private final String phase;
    private final String billingPeriod;
    private final BigDecimal price;
    private final String priceList;
    private final BigDecimal mrr;
    private final String currency;
    private final String state;
    private final Boolean businessActive;
    private final DateTime startDate;
    private final DateTime endDate;

    public BusinessSubscription(@Nullable final Plan currentPlan,
                                @Nullable final PlanPhase currentPhase,
                                @Nullable final PriceList priceList,
                                final Currency currency,
                                final DateTime startDate,
                                final SubscriptionState state) {
        // TODO
        businessActive = true;

        this.priceList = priceList == null ? null : priceList.getName();

        // Record plan information
        if (currentPlan != null && currentPlan.getProduct() != null) {
            final Product product = currentPlan.getProduct();
            productName = product.getName();
            productCategory = product.getCategory().toString();
            productType = product.getCatalogName();
        } else {
            productName = null;
            productCategory = null;
            productType = null;
        }

        // Record phase information
        if (currentPhase != null) {
            slug = currentPhase.getName();

            if (currentPhase.getPhaseType() != null) {
                phase = currentPhase.getPhaseType().toString();
            } else {
                phase = null;
            }

            if (currentPhase.getBillingPeriod() != null) {
                billingPeriod = currentPhase.getBillingPeriod().toString();
            } else {
                billingPeriod = null;
            }

            if (currentPhase.getRecurringPrice() != null) {
                BigDecimal tmpPrice;
                try {
                    tmpPrice = currentPhase.getRecurringPrice().getPrice(USD);
                } catch (CatalogApiException e) {
                    tmpPrice = null;
                }

                price = tmpPrice;
                if (tmpPrice != null) {
                    mrr = getMrrFromBillingPeriod(currentPhase.getBillingPeriod(), price);
                } else {
                    mrr = null;
                }
            } else {
                price = BigDecimal.ZERO;
                mrr = BigDecimal.ZERO;
            }
        } else {
            slug = null;
            phase = null;
            billingPeriod = null;
            price = BigDecimal.ZERO;
            mrr = BigDecimal.ZERO;
        }

        if (currency != null) {
            this.currency = currency.toString();
        } else {
            this.currency = null;
        }

        this.startDate = startDate;
        if (currentPhase != null) {
            final Duration duration = currentPhase.getDuration();
            this.endDate = duration == null ? null : startDate.plus(duration.toJodaPeriod());
        } else {
            this.endDate = null;
        }
        this.state = state == null ? null : state.toString();
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMrr() {
        return mrr;
    }

    public double getRoundedMrr() {
        return Rounder.round(mrr);
    }

    public String getPhase() {
        return phase;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPriceList() {
        return priceList;
    }

    public double getRoundedPrice() {
        return Rounder.round(price);
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductType() {
        return productType;
    }

    public String getSlug() {
        return slug;
    }

    public Boolean getBusinessActive() {
        return businessActive;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public String getState() {
        return state;
    }

    static BigDecimal getMrrFromBillingPeriod(final BillingPeriod period, final BigDecimal price) {
        if (period == null || period.getNumberOfMonths() == 0) {
            return BigDecimal.ZERO;
        }

        return price.divide(BigDecimal.valueOf(period.getNumberOfMonths()), Rounder.SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscription");
        sb.append("{productName='").append(productName).append('\'');
        sb.append(", productType='").append(productType).append('\'');
        sb.append(", productCategory=").append(productCategory);
        sb.append(", slug='").append(slug).append('\'');
        sb.append(", phase='").append(phase).append('\'');
        sb.append(", billingPeriod='").append(billingPeriod).append('\'');
        sb.append(", price=").append(price);
        sb.append(", priceList='").append(priceList).append('\'');
        sb.append(", mrr=").append(mrr);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", state=").append(state);
        sb.append(", businessActive=").append(businessActive);
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
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

        final BusinessSubscription that = (BusinessSubscription) o;

        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (businessActive != null ? !businessActive.equals(that.businessActive) : that.businessActive != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (mrr != null ? !mrr.equals(that.mrr) : that.mrr != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (price != null ? !price.equals(that.price) : that.price != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (productCategory != that.productCategory) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (productType != null ? !productType.equals(that.productType) : that.productType != null) {
            return false;
        }
        if (slug != null ? !slug.equals(that.slug) : that.slug != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (state != that.state) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = productName != null ? productName.hashCode() : 0;
        result = 31 * result + (productType != null ? productType.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (slug != null ? slug.hashCode() : 0);
        result = 31 * result + (phase != null ? phase.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (mrr != null ? mrr.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (businessActive != null ? businessActive.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
