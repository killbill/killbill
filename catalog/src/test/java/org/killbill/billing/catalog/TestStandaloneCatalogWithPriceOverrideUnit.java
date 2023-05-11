/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.time.Instant;
import java.util.Date;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestStandaloneCatalogWithPriceOverrideUnit extends CatalogTestSuiteNoDB {

    private StandaloneCatalog newStandalone(final String name) {
        return new StandaloneCatalog().setCatalogName(name);
    }

    private StandaloneCatalogWithPriceOverride newPriceOverride(final String name) {
        final StandaloneCatalogWithPriceOverride override = new StandaloneCatalogWithPriceOverride();
        override.setCatalogName(name);
        return override;
    }

    @Test(groups = "fast")
    public void testEquality() {
        final StandaloneCatalog standalone1 = newStandalone("catalog-name");
        final StandaloneCatalog standalone2 = newStandalone("catalog-name");
        Assert.assertEquals(standalone1, standalone2);

        final StandaloneCatalogWithPriceOverride override1 = newPriceOverride("catalog-name");
        final StandaloneCatalogWithPriceOverride override2 = newPriceOverride("catalog-name");
        Assert.assertEquals(override1, override2);

        //
        Assert.assertEquals(standalone1, override1);
        Assert.assertEquals(standalone1, override2);
        Assert.assertEquals(override1, standalone1);
        Assert.assertEquals(override2, standalone1);
        Assert.assertEquals(override2, standalone2);

        // Different effectiveDate
        final StandaloneCatalog standalone3 = newStandalone("catalog-name");
        standalone3.setEffectiveDate(Date.from(Instant.now()));

        Assert.assertNotEquals(standalone3, standalone1);
        Assert.assertNotEquals(standalone3, override2);

        //

        final StandaloneCatalog anotherStandalone = newStandalone("another-name");
        final StandaloneCatalogWithPriceOverride anotherOverride = newPriceOverride("another-name");
        Assert.assertEquals(anotherStandalone, anotherOverride);

        Assert.assertNotEquals(standalone1, anotherStandalone);
        Assert.assertNotEquals(standalone2, anotherStandalone);
        Assert.assertNotEquals(override1, anotherOverride);
        Assert.assertNotEquals(override2, anotherOverride);
    }
}
