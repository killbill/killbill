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

package com.ning.billing.jaxrs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.CatalogJsonSimple;
import com.ning.billing.jaxrs.json.CatalogJsonSimple.PlanJson;
import com.ning.billing.jaxrs.json.CatalogJsonSimple.ProductJson;
import com.ning.billing.jaxrs.json.PlanDetailJson;

public class TestCatalog extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testCatalogSimple() throws Exception {
        final Set<String> allBasePlans = new HashSet<String>();

        final CatalogJsonSimple catalogJsonSimple = getSimpleCatalog();
        for (final ProductJson productJson : catalogJsonSimple.getProducts()) {
            if (!"BASE".equals(productJson.getType())) {
                Assert.assertEquals(productJson.getIncluded().size(), 0);
                Assert.assertEquals(productJson.getAvailable().size(), 0);
                continue;
            }

            // Save all plans for later (see below)
            for (final PlanJson planJson : productJson.getPlans()) {
                allBasePlans.add(planJson.getName());
            }

            // Retrieve available products (addons) for that base product
            final List<PlanDetailJson> availableAddons = getAvailableAddons(productJson.getName());
            final Set<String> availableAddonsNames = new HashSet<String>();
            for (final PlanDetailJson planDetailJson : availableAddons) {
                availableAddonsNames.add(planDetailJson.getProductName());
            }
            Assert.assertEquals(availableAddonsNames, new HashSet<String>(productJson.getAvailable()));
        }

        // Verify base plans endpoint
        final List<PlanDetailJson> basePlans = getBasePlans();
        final Set<String> foundBasePlans = new HashSet<String>();
        for (final PlanDetailJson planDetailJson : basePlans) {
            foundBasePlans.add(planDetailJson.getPlanName());
        }
        Assert.assertEquals(foundBasePlans, allBasePlans);
    }
}
