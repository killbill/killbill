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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPlanDetailJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String productName = UUID.randomUUID().toString();
        final String planName = UUID.randomUUID().toString();
        final BillingPeriod billingPeriod = BillingPeriod.ANNUAL;
        final String priceListName = UUID.randomUUID().toString();
        final PlanDetailJson planDetailJason = new PlanDetailJson(productName, planName, billingPeriod, priceListName, null);
        Assert.assertEquals(planDetailJason.getProduct(), productName);
        Assert.assertEquals(planDetailJason.getPlan(), planName);
        Assert.assertEquals(planDetailJason.getFinalPhaseBillingPeriod(), billingPeriod);
        Assert.assertEquals(planDetailJason.getPriceList(), priceListName);
        Assert.assertEquals(planDetailJason.getFinalPhaseRecurringPrice(), null);

        final String asJson = mapper.writeValueAsString(planDetailJason);
        Assert.assertEquals(asJson, "{\"product\":\"" + planDetailJason.getProduct() + "\"," +
                                    "\"plan\":\"" + planDetailJason.getPlan() + "\"," +
                                    "\"priceList\":\"" + planDetailJason.getPriceList() + "\"," +
                                    "\"finalPhaseBillingPeriod\":\"" + planDetailJason.getFinalPhaseBillingPeriod().toString() + "\"," +
                                    "\"finalPhaseRecurringPrice\":null}");

        final PlanDetailJson fromJson = mapper.readValue(asJson, PlanDetailJson.class);
        Assert.assertEquals(fromJson, planDetailJason);
    }

    @Test(groups = "fast")
    public void testFromListing() throws Exception {
        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn(UUID.randomUUID().toString());

        final InternationalPrice price = Mockito.mock(InternationalPrice.class);
        final Price[] mock = {};
        Mockito.when(price.getPrices()).thenReturn(mock);
        final PlanPhase planPhase = Mockito.mock(PlanPhase.class);
        final Recurring recurring = Mockito.mock(Recurring.class);
        Mockito.when(recurring.getRecurringPrice()).thenReturn(price);
        Mockito.when(planPhase.getRecurring()).thenReturn(recurring);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getProduct()).thenReturn(product);
        Mockito.when(plan.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(plan.getRecurringBillingPeriod()).thenReturn(BillingPeriod.QUARTERLY);
        Mockito.when(plan.getFinalPhase()).thenReturn(planPhase);

        final PriceList priceList = Mockito.mock(PriceList.class);
        Mockito.when(priceList.getName()).thenReturn(UUID.randomUUID().toString());

        final Listing listing = Mockito.mock(Listing.class);
        Mockito.when(listing.getPlan()).thenReturn(plan);
        Mockito.when(listing.getPriceList()).thenReturn(priceList);

        final PlanDetailJson planDetailJson = new PlanDetailJson(listing);
        Assert.assertEquals(planDetailJson.getProduct(), plan.getProduct().getName());
        Assert.assertEquals(planDetailJson.getPlan(), plan.getName());
        Assert.assertEquals(planDetailJson.getFinalPhaseBillingPeriod(), plan.getRecurringBillingPeriod());
        Assert.assertEquals(planDetailJson.getPriceList(), priceList.getName());
        Assert.assertEquals(planDetailJson.getFinalPhaseRecurringPrice().size(), 0);
    }
}
