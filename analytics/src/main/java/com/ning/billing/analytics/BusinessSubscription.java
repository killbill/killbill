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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static com.ning.billing.entitlement.api.user.ISubscription.SubscriptionState;

/**
 * Describe a subscription for Analytics purposes
 */
public class BusinessSubscription
{
    private static final Logger log = LoggerFactory.getLogger(BusinessSubscription.class);

    private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    private static final Currency USD = Currency.valueOf("USD");
    private static final int SCALE = 4;

    private final String productName;
    private final String productType;
    private final ProductCategory productCategory;
    private final String slug;
    private final String phase;
    private final BigDecimal price;
    private final BigDecimal mrr;
    private final String currency;
    private final DateTime startDate;
    private final SubscriptionState state;
    private final UUID subscriptionId;
    private final UUID bundleId;

    public BusinessSubscription(final String productName, final String productType, final ProductCategory productCategory, final String slug, final String phase, final BigDecimal price, final BigDecimal mrr, final String currency, final DateTime startDate, final SubscriptionState state, final UUID subscriptionId, final UUID bundleId)
    {
        this.productName = productName;
        this.productType = productType;
        this.productCategory = productCategory;
        this.slug = slug;
        this.phase = phase;
        this.price = price;
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
    BusinessSubscription(final ISubscription subscription, final Currency currency)
    {
        this(subscription.getCurrentPlan(), subscription.getCurrentPhase(), currency, subscription.getStartDate(), subscription.getState(), subscription.getId(), subscription.getBundleId());
    }

    public BusinessSubscription(final IPlan currentPlan, final IPlanPhase currentPhase, final Currency currency, final DateTime startDate, final SubscriptionState state, final UUID subscriptionId, final UUID bundleId)
    {
        // Record plan information
        if (currentPlan != null && currentPlan.getProduct() != null) {
            final IProduct product = currentPlan.getProduct();
            productName = product.getName();
            productCategory = product.getCategory();
            if (product.getCatalogName() != null) {
                productType = product.getCatalogName();
            }
            else {
                productType = null;
            }
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

            if (currentPhase.getRecurringPrice() != null) {
                price = currentPhase.getRecurringPrice().getPrice(USD);
                mrr = getMrrFromISubscription(currentPhase.getDuration(), price);
            }
            else {
                price = null;
                mrr = null;
            }
        }
        else {
            slug = null;
            phase = null;
            price = null;
            mrr = null;
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
        return round(mrr);
    }

    public String getPhase()
    {
        return phase;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public double getRoundedPrice()
    {
        return round(price);
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

    static BigDecimal getMrrFromISubscription(final IDuration duration, final BigDecimal price)
    {
        if (duration == null || duration.getUnit() == null || duration.getLength() == 0) {
            return null;
        }

        if (duration.getUnit().equals(TimeUnit.UNLIMITED)) {
            return BigDecimal.ZERO;
        }
        else if (duration.getUnit().equals(TimeUnit.DAYS)) {
            return price.multiply(DAYS_IN_MONTH).multiply(BigDecimal.valueOf(duration.getLength()));
        }
        else if (duration.getUnit().equals(TimeUnit.MONTHS)) {
            return price.divide(BigDecimal.valueOf(duration.getLength()), SCALE, BigDecimal.ROUND_HALF_UP);
        }
        else if (duration.getUnit().equals(TimeUnit.YEARS)) {
            return price.divide(BigDecimal.valueOf(duration.getLength()), SCALE, RoundingMode.HALF_UP).divide(MONTHS_IN_YEAR, SCALE, RoundingMode.HALF_UP);
        }
        else {
            log.error("Unknown duration [" + duration + "], can't compute mrr");
            return null;
        }
    }

    public static double round(final BigDecimal decimal)
    {
        if (decimal == null) {
            return 0;
        }
        else {
            return decimal.setScale(SCALE, BigDecimal.ROUND_HALF_UP).doubleValue();
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscription");
        sb.append("{bundleId=").append(bundleId);
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", productType='").append(productType).append('\'');
        sb.append(", productCategory='").append(productCategory).append('\'');
        sb.append(", slug='").append(slug).append('\'');
        sb.append(", phase='").append(phase).append('\'');
        sb.append(", price=").append(price);
        sb.append(", mrr=").append(mrr);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", state=").append(state);
        sb.append(", subscriptionId=").append(subscriptionId);
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

        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (mrr != null ? !(round(mrr) == round(that.mrr)) : that.mrr != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (price != null ? !(round(price) == round(that.price)) : that.price != null) {
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
        result = 31 * result + (mrr != null ? mrr.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        return result;
    }
}
