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

import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static com.ning.billing.catalog.api.Currency.USD;

public class TestBusinessSubscription
{
    private final IDuration MONTHLY = MockDuration.MONHTLY();
    private final IDuration YEARLY = MockDuration.YEARLY();
    final Object[][] catalog = {
        {MONTHLY, 229.0000, 229.0000},
        {MONTHLY, 19.9500, 19.9500},
        {MONTHLY, 14.9500, 14.9500},
        {MONTHLY, 12.9500, 12.9500},
        {YEARLY, 19.9500, 1.6625},
        {YEARLY, 399.0000, 33.2500},
        {YEARLY, 29.9500, 2.4958},
        {YEARLY, 59.0000, 4.9167},
        {YEARLY, 18.2900, 1.5242},
        {YEARLY, 49.0000, 4.0833}};

    private IProduct product;
    private IPlan plan;
    private IPlanPhase phase;
    private ISubscription isubscription;
    private BusinessSubscription subscription;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception
    {
        product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        plan = new MockPlan("platinum-monthly", product);
        phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
        isubscription = new MockSubscription(ISubscription.SubscriptionState.ACTIVE, plan, phase);
        subscription = new BusinessSubscription(isubscription, USD);
    }

    @Test(groups = "fast")
    public void testMrrComputation() throws Exception
    {
        int i = 0;
        for (final Object[] object : catalog) {
            final IDuration duration = (IDuration) object[0];
            final double price = (Double) object[1];
            final double expectedMrr = (Double) object[2];

            final BigDecimal computedMrr = BusinessSubscription.getMrrFromISubscription(duration, BigDecimal.valueOf(price));
            Assert.assertEquals(computedMrr.doubleValue(), expectedMrr, "Invalid mrr for product #" + i);
            i++;
        }
    }

    @Test(groups = "fast")
    public void testConstructor() throws Exception
    {
        Assert.assertEquals(subscription.getRoundedMrr(), 0.0);
        Assert.assertEquals(subscription.getSlug(), phase.getName());
        Assert.assertEquals(subscription.getPhase(), phase.getPhaseType().toString());
        Assert.assertEquals(subscription.getPrice(), phase.getRecurringPrice().getPrice(null));
        Assert.assertEquals(subscription.getProductCategory(), product.getCategory());
        Assert.assertEquals(subscription.getProductName(), product.getName());
        Assert.assertEquals(subscription.getProductType(), product.getCatalogName());
        Assert.assertEquals(subscription.getStartDate(), isubscription.getStartDate());
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception
    {
        Assert.assertSame(subscription, subscription);
        Assert.assertEquals(subscription, subscription);
        Assert.assertTrue(subscription.equals(subscription));

        final ISubscription otherIsubscription = new MockSubscription(ISubscription.SubscriptionState.CANCELLED, plan, phase);
        Assert.assertTrue(!subscription.equals(new BusinessSubscription(otherIsubscription, USD)));
    }
}
