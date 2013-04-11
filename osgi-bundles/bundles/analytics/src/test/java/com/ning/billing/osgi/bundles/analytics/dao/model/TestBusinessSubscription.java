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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessSubscription extends AnalyticsTestSuiteNoDB {

    final Object[][] catalogMapping = {
            {BillingPeriod.NO_BILLING_PERIOD, 369.9500, 0.0000},
            {BillingPeriod.NO_BILLING_PERIOD, 429.9500, 0.0000},
            {BillingPeriod.NO_BILLING_PERIOD, 999.9500, 0.0000},
            {BillingPeriod.NO_BILLING_PERIOD, 2300.0000, 0.0000},
            {BillingPeriod.MONTHLY, 2.9500, 2.9500},
            {BillingPeriod.MONTHLY, 3.9500, 3.9500},
            {BillingPeriod.MONTHLY, 6.9500, 6.9500},
            {BillingPeriod.MONTHLY, 7.0000, 7.0000},
            {BillingPeriod.MONTHLY, 7.9500, 7.9500},
            {BillingPeriod.MONTHLY, 9.0000, 9.0000},
            {BillingPeriod.MONTHLY, 9.9500, 9.9500},
            {BillingPeriod.MONTHLY, 11.9500, 11.9500},
            {BillingPeriod.MONTHLY, 12.4500, 12.4500},
            {BillingPeriod.MONTHLY, 12.9500, 12.9500},
            {BillingPeriod.MONTHLY, 14.9500, 14.9500},
            {BillingPeriod.MONTHLY, 15.0000, 15.0000},
            {BillingPeriod.MONTHLY, 16.9500, 16.9500},
            {BillingPeriod.MONTHLY, 19.0000, 19.0000},
            {BillingPeriod.MONTHLY, 19.9500, 19.9500},
            {BillingPeriod.MONTHLY, 24.9500, 24.9500},
            {BillingPeriod.MONTHLY, 29.0000, 29.0000},
            {BillingPeriod.MONTHLY, 29.9500, 29.9500},
            {BillingPeriod.MONTHLY, 31.0000, 31.0000},
            {BillingPeriod.MONTHLY, 34.9500, 34.9500},
            {BillingPeriod.MONTHLY, 39.0000, 39.0000},
            {BillingPeriod.MONTHLY, 39.9500, 39.9500},
            {BillingPeriod.MONTHLY, 49.0000, 49.0000},
            {BillingPeriod.MONTHLY, 49.9500, 49.9500},
            {BillingPeriod.MONTHLY, 59.9500, 59.9500},
            {BillingPeriod.MONTHLY, 79.0000, 79.0000},
            {BillingPeriod.MONTHLY, 99.0000, 99.0000},
            {BillingPeriod.MONTHLY, 139.0000, 139.0000},
            {BillingPeriod.MONTHLY, 209.0000, 209.0000},
            {BillingPeriod.MONTHLY, 229.0000, 229.0000},
            {BillingPeriod.MONTHLY, 274.5000, 274.5000},
            {BillingPeriod.MONTHLY, 549.0000, 549.0000},
            {BillingPeriod.ANNUAL, 18.2900, 1.5242},
            {BillingPeriod.ANNUAL, 19.9500, 1.6625},
            {BillingPeriod.ANNUAL, 29.9500, 2.4958},
            {BillingPeriod.ANNUAL, 49.0000, 4.0833},
            {BillingPeriod.ANNUAL, 59.0000, 4.9167},
            {BillingPeriod.ANNUAL, 149.9500, 12.4958},
            {BillingPeriod.ANNUAL, 159.9500, 13.3292},
            {BillingPeriod.ANNUAL, 169.9500, 14.1625},
            {BillingPeriod.ANNUAL, 183.2900, 15.2742},
            {BillingPeriod.ANNUAL, 199.9500, 16.6625},
            {BillingPeriod.ANNUAL, 219.9500, 18.3292},
            {BillingPeriod.ANNUAL, 239.9000, 19.9917},
            {BillingPeriod.ANNUAL, 249.9500, 20.8292},
            {BillingPeriod.ANNUAL, 319.0000, 26.5833},
            {BillingPeriod.ANNUAL, 349.9500, 29.1625},
            {BillingPeriod.ANNUAL, 399.0000, 33.2500},
            {BillingPeriod.ANNUAL, 399.9500, 33.3292},
            {BillingPeriod.ANNUAL, 458.2900, 38.1908},
            {BillingPeriod.ANNUAL, 499.9500, 41.6625},
            {BillingPeriod.ANNUAL, 549.9500, 45.8292},
            {BillingPeriod.ANNUAL, 599.9000, 49.9917},
            {BillingPeriod.ANNUAL, 599.9500, 49.9958},
            {BillingPeriod.ANNUAL, 624.9500, 52.0792},
            {BillingPeriod.ANNUAL, 799.0000, 66.5833},
            {BillingPeriod.ANNUAL, 999.0000, 83.2500},
            {BillingPeriod.ANNUAL, 2299.0000, 191.5833},
            {BillingPeriod.ANNUAL, 5499.0000, 458.2500}};

    @Test(groups = "fast")
    public void testMrrComputation() throws Exception {
        int i = 0;
        for (final Object[] object : catalogMapping) {
            final BillingPeriod billingPeriod = (BillingPeriod) object[0];
            final double price = (Double) object[1];
            final double expectedMrr = (Double) object[2];

            final BigDecimal computedMrr = BusinessSubscription.getMrrFromBillingPeriod(billingPeriod, BigDecimal.valueOf(price));
            Assert.assertEquals(computedMrr.doubleValue(), expectedMrr, "Invalid mrr for product #" + i);
            i++;
        }
    }

    @Test(groups = "fast")
    public void testConstructorWithNulls() throws Exception {
        final DateTime startDate = new DateTime(2019, 7, 4, 23, 3, 11, DateTimeZone.UTC);
        final BusinessSubscription businessSubscription = new BusinessSubscription(null,
                                                                                   null,
                                                                                   null,
                                                                                   Currency.GBP,
                                                                                   startDate,
                                                                                   SubscriptionState.ACTIVE);
        Assert.assertNull(businessSubscription.getProductName());
        Assert.assertNull(businessSubscription.getProductType());
        Assert.assertNull(businessSubscription.getProductCategory());
        Assert.assertNull(businessSubscription.getSlug());
        Assert.assertNull(businessSubscription.getPhase());
        Assert.assertNull(businessSubscription.getBillingPeriod());
        Assert.assertNull(businessSubscription.getPrice());
        Assert.assertNull(businessSubscription.getPriceList(), priceList.getName());
        Assert.assertEquals(businessSubscription.getCurrency(), Currency.GBP.toString());
        Assert.assertEquals(businessSubscription.getState(), SubscriptionState.ACTIVE.toString());
        //Assert.assertEquals(businessSubscription.getBusinessActive(), /* TODO */);
        Assert.assertEquals(businessSubscription.getStartDate(), startDate);
        Assert.assertNull(businessSubscription.getEndDate());

    }

    @Test(groups = "fast")
    public void testConstructorWithoutNulls() throws Exception {
        final DateTime startDate = new DateTime(2019, 7, 4, 9, 3, 11, DateTimeZone.UTC);
        final BusinessSubscription businessSubscription = new BusinessSubscription(plan,
                                                                                   phase,
                                                                                   priceList,
                                                                                   Currency.GBP,
                                                                                   startDate,
                                                                                   SubscriptionState.ACTIVE);
        Assert.assertEquals(businessSubscription.getProductName(), plan.getProduct().getName());
        Assert.assertEquals(businessSubscription.getProductType(), plan.getProduct().getCatalogName());
        Assert.assertEquals(businessSubscription.getProductCategory(), plan.getProduct().getCategory().toString());
        Assert.assertEquals(businessSubscription.getSlug(), phase.getName());
        Assert.assertEquals(businessSubscription.getPhase(), phase.getPhaseType().toString());
        Assert.assertEquals(businessSubscription.getBillingPeriod(), phase.getBillingPeriod().toString());
        Assert.assertEquals(businessSubscription.getPrice(), phase.getRecurringPrice());
        Assert.assertEquals(businessSubscription.getPriceList(), priceList.getName());
        Assert.assertEquals(businessSubscription.getCurrency(), Currency.GBP.toString());
        Assert.assertEquals(businessSubscription.getState(), SubscriptionState.ACTIVE.toString());
        //Assert.assertEquals(businessSubscription.getBusinessActive(), /* TODO */);
        Assert.assertEquals(businessSubscription.getStartDate(), startDate);
        Assert.assertNull(businessSubscription.getEndDate());
    }
}
