/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Listing;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.jaxrs.JaxrsTestSuite;

public class TestPlanDetailJason extends JaxrsTestSuite {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String productName = UUID.randomUUID().toString();
        final String planName = UUID.randomUUID().toString();
        final BillingPeriod billingPeriod = BillingPeriod.ANNUAL;
        final String priceListName = UUID.randomUUID().toString();
        final PlanDetailJason planDetailJason = new PlanDetailJason(productName, planName, billingPeriod, priceListName, null);
        Assert.assertEquals(planDetailJason.getProductName(), productName);
        Assert.assertEquals(planDetailJason.getPlanName(), planName);
        Assert.assertEquals(planDetailJason.getBillingPeriod(), billingPeriod);
        Assert.assertEquals(planDetailJason.getPriceListName(), priceListName);
        Assert.assertEquals(planDetailJason.getFinalPhasePrice(), null);

        final String asJson = mapper.writeValueAsString(planDetailJason);
        Assert.assertEquals(asJson, "{\"productName\":\"" + planDetailJason.getProductName() + "\"," +
                "\"planName\":\"" + planDetailJason.getPlanName() + "\"," +
                "\"billingPeriod\":\"" + planDetailJason.getBillingPeriod().toString() + "\"," +
                "\"priceListName\":\"" + planDetailJason.getPriceListName() + "\"," +
                "\"finalPhasePrice\":null}");

        final PlanDetailJason fromJson = mapper.readValue(asJson, PlanDetailJason.class);
        Assert.assertEquals(fromJson, planDetailJason);
    }

    @Test(groups = "fast")
    public void testFromListing() throws Exception {
        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn(UUID.randomUUID().toString());

        final InternationalPrice price = Mockito.mock(InternationalPrice.class);
        final PlanPhase planPhase = Mockito.mock(PlanPhase.class);
        Mockito.when(planPhase.getRecurringPrice()).thenReturn(price);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getProduct()).thenReturn(product);
        Mockito.when(plan.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(plan.getBillingPeriod()).thenReturn(BillingPeriod.QUARTERLY);
        Mockito.when(plan.getFinalPhase()).thenReturn(planPhase);

        final PriceList priceList = Mockito.mock(PriceList.class);
        Mockito.when(priceList.getName()).thenReturn(UUID.randomUUID().toString());

        final Listing listing = Mockito.mock(Listing.class);
        Mockito.when(listing.getPlan()).thenReturn(plan);
        Mockito.when(listing.getPriceList()).thenReturn(priceList);

        final PlanDetailJason planDetailJason = new PlanDetailJason(listing);
        Assert.assertEquals(planDetailJason.getProductName(), plan.getProduct().getName());
        Assert.assertEquals(planDetailJason.getPlanName(), plan.getName());
        Assert.assertEquals(planDetailJason.getBillingPeriod(), plan.getBillingPeriod());
        Assert.assertEquals(planDetailJason.getPriceListName(), priceList.getName());
        Assert.assertEquals(planDetailJason.getFinalPhasePrice(), plan.getFinalPhase().getRecurringPrice());
    }
}
