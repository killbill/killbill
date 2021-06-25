/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.catalog.caching;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

// Matrix 2 *2 of regex test cases, i.e {delimiter * optional catalog version}
public class TestPriceOverridePattern extends CatalogTestSuiteNoDB {


    @Test(groups = "fast")
    public void testWithLegacyCustomPlanNameDelimiter1() throws CatalogApiException {
        final PriceOverridePattern priceOverridePattern = new PriceOverridePattern(false);
        final String legacyOverridddenPlanName = "foo-12345";
        final String[] parts  = priceOverridePattern.getPlanParts(legacyOverridddenPlanName);
        Assert.assertEquals(parts.length, 2);
        Assert.assertEquals(parts[0], "foo");
        Assert.assertEquals(parts[1], "12345");
    }

    @Test(groups = "fast")
    public void testWithLegacyCustomPlanNameDelimiter2() throws CatalogApiException {
        final PriceOverridePattern priceOverridePattern = new PriceOverridePattern(false);
        final String legacyOverridddenPlanName = "foo-12345!8765432";
        final String[] parts  = priceOverridePattern.getPlanParts(legacyOverridddenPlanName);
        Assert.assertEquals(parts.length, 2);
        Assert.assertEquals(parts[0], "foo");
        Assert.assertEquals(parts[1], "12345");
    }

    @Test(groups = "fast")
    public void testWithNonLegacyCustomPlanNameDelimiter1() throws CatalogApiException {
        final PriceOverridePattern priceOverridePattern = new PriceOverridePattern(true);
        final String legacyOverridddenPlanName = "foo:12345";
        final String[] parts  = priceOverridePattern.getPlanParts(legacyOverridddenPlanName);
        Assert.assertEquals(parts.length, 2);
        Assert.assertEquals(parts[0], "foo");
        Assert.assertEquals(parts[1], "12345");
    }

    @Test(groups = "fast")
    public void testWithNonLegacyCustomPlanNameDelimiter2() throws CatalogApiException {
        final PriceOverridePattern priceOverridePattern = new PriceOverridePattern(true);
        final String legacyOverridddenPlanName = "foo:12345!8765432";
        final String[] parts  = priceOverridePattern.getPlanParts(legacyOverridddenPlanName);
        Assert.assertEquals(parts.length, 2);
        Assert.assertEquals(parts[0], "foo");
        Assert.assertEquals(parts[1], "12345");
    }
}