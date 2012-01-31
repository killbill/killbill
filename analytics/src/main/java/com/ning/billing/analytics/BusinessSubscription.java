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

package com.ning.billing.analytics;

import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.entitlement.api.user.Subscription;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;

/**
 * Describe a subscription for Analytics purposes
 */
public class BusinessSubscription
{
    private static final Logger log = LoggerFactory.getLogger(BusinessSubscription.class);

    private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    private static final Currency USD = Currency.valueOf("USD");

    private final String productName;
    private final String productType;
    private final ProductCategory productCategory;
    private final String slug;
    private final String phase;
    private final String billingPeriod;
    private final BigDecimal price;
    private final String priceList;
    private final BigDecimal mrr;
    private final String currency;
    private final DateTime startDate;
    private final SubscriptionState state;
    private final UUID subscriptionId;
    private final UUID bundleId;

    public BusinessSubscription(final String productName, final String productType, final ProductCategory productCategory, final String slug, final String phase, final String billingPeriod, final BigDecimal price, final String priceList, final BigDecimal mrr, final String currency, final DateTime startDate, final SubscriptionState state, final UUID subscriptionId, final UUID bundleId)
    {
        this.productName = productName;
        this.productType = productType;
        this.productCategory = productCategory;
        this.slug = slug;
        this.phase = phase;
        this.billingPeriod = billingPeriod;
        this.price = price;
        this.priceList = priceList;
        this.mrr = mrr;
        this.currency = currency;
        this.startDate = startDate;
        this.state = state;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
    }

    /**
     * For unit tests only.
     * <p/>
     * You can't really use this constructor in real life because the start date is likely not the one you want (you likely
     * want the phase start date).
     *
     * @param subscription Subscription to use as a model
     * @param currency     Account currency
     */
    BusinessSubscription(final Subscription subscription, final Currency currency)
    {
        this(subscription.getCurrentPriceList(), subscription.getCurrentPlan(), subscription.getCurrentPhase(), currency, subscription.getStartDate(), subscription.getState(), subscription.getId(), subscription.getBundleId());
    }

    public BusinessSubscription(final String priceList, final Plan currentPlan, final PlanPhase currentPhase, final Currency currency, final DateTime startDate, final SubscriptionState state, final UUID subscriptionId, final UUID bundleId)
    {
        this.priceList = priceList;

        // Record plan information
        if (currentPlan != null && currentPlan.getProduct() != null) {
            final Product product = currentPlan.getProduct();
            productName = product.getName();
            productCategory = product.getCategory();
            // TODO - we should keep the product type
            productType = product.getCatalogName();
        }
        else {
            productName = null;
            productCategory = null;
            productType = null;
        }

        // Record phase information
        if (currentPhase != null) {
            slug = currentPhase.getName();

            if (currentPhase.getPhaseType() != null) {
                phase = currentPhase.getPhaseType().toString();
            }
            else {
                phase = null;
            }

            if (currentPhase.getBillingPeriod() != null) {
                billingPeriod = currentPhase.getBillingPeriod().toString();
            }
            else {
                billingPeriod = null;
            }

            if (currentPhase.getRecurringPrice() != null) {
            	//TODO check if this is the right way to handle exception
            	BigDecimal tmpPrice = null;
                try {
                	tmpPrice = currentPhase.getRecurringPrice().getPrice(USD);
				} catch (CatalogApiException e) {
					tmpPrice = new BigDecimal(0);
				}
                price = tmpPrice;
                mrr = getMrrFromISubscription(currentPhase.getDuration(), price);
            }
            else {
                price = BigDecimal.ZERO;
                mrr = BigDecimal.ZERO;
            }
        }
        else {
            slug = null;
            phase = null;
            billingPeriod = null;
            price = BigDecimal.ZERO;
            mrr = BigDecimal.ZERO;
        }

        if (currency != null) {
            this.currency = currency.toString();
        }
        else {
            this.currency = null;
        }

        this.startDate = startDate;
        this.state = state;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
    }

    public String getBillingPeriod()
    {
        return billingPeriod;
    }

    public UUID getBundleId()
    {
        return bundleId;
    }

    public String getCurrency()
    {
        return currency;
    }

    public BigDecimal getMrr()
    {
        return mrr;
    }

    public double getRoundedMrr()
    {
        return Rounder.round(mrr);
    }

    public String getPhase()
    {
        return phase;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public String getPriceList()
    {
        return priceList;
    }

    public double getRoundedPrice()
    {
        return Rounder.round(price);
    }

    public ProductCategory getProductCategory()
    {
        return productCategory;
    }

    public String getProductName()
    {
        return productName;
    }

    public String getProductType()
    {
        return productType;
    }

    public String getSlug()
    {
        return slug;
    }

    public DateTime getStartDate()
    {
        return startDate;
    }

    public SubscriptionState getState()
    {
        return state;
    }

    public UUID getSubscriptionId()
    {
        return subscriptionId;
    }

    static BigDecimal getMrrFromISubscription(final Duration duration, final BigDecimal price)
    {
        if (duration == null || duration.getUnit() == null || duration.getNumber() == 0) {
            return BigDecimal.ZERO;
        }

        if (duration.getUnit().equals(TimeUnit.UNLIMITED)) {
            return BigDecimal.ZERO;
        }
        else if (duration.getUnit().equals(TimeUnit.DAYS)) {
            return price.multiply(DAYS_IN_MONTH).multiply(BigDecimal.valueOf(duration.getNumber()));
        }
        else if (duration.getUnit().equals(TimeUnit.MONTHS)) {
            return price.divide(BigDecimal.valueOf(duration.getNumber()), Rounder.SCALE, BigDecimal.ROUND_HALF_UP);
        }
        else if (duration.getUnit().equals(TimeUnit.YEARS)) {
            return price.divide(BigDecimal.valueOf(duration.getNumber()), Rounder.SCALE, RoundingMode.HALF_UP).divide(MONTHS_IN_YEAR, Rounder.SCALE, RoundingMode.HALF_UP);
        }
        else {
            log.error("Unknown duration [" + duration + "], can't compute mrr");
            return null;
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscription");
        sb.append("{billingPeriod='").append(billingPeriod).append('\'');
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", productType='").append(productType).append('\'');
        sb.append(", productCategory=").append(productCategory);
        sb.append(", slug='").append(slug).append('\'');
        sb.append(", phase='").append(phase).append('\'');
        sb.append(", price=").append(price);
        sb.append(", priceList=").append(priceList);
        sb.append(", mrr=").append(mrr);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", state=").append(state);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", bundleId=").append(bundleId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o)
    {
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
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (mrr != null ? !(Rounder.round(mrr) == Rounder.round(that.mrr)) : that.mrr != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (price != null ? !(Rounder.round(price) == Rounder.round(that.price)) : that.price != null) {
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
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = productName != null ? productName.hashCode() : 0;
        result = 31 * result + (productType != null ? productType.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (slug != null ? slug.hashCode() : 0);
        result = 31 * result + (phase != null ? phase.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (mrr != null ? mrr.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        return result;
    }
}
