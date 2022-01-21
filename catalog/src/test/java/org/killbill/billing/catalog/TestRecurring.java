/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import org.killbill.billing.catalog.api.Currency;
import org.killbill.xmlloader.ValidationErrors;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestRecurring extends CatalogTestSuiteNoDB {

    private static MockCatalog createCatalog() {
        final MockCatalog catalog = new MockCatalog();
        catalog.setSupportedCurrencies(new Currency[] {Currency.USD});
        return catalog;
    }

    @Test(groups = "fast")
    public void testValidRecurring() {
        final MockCatalog catalog = createCatalog();

        final DefaultRecurring recurring = MockRecurring.validRecurring();
        recurring.initialize(catalog);

        final ValidationErrors errors = recurring.validate(catalog, new ValidationErrors());
        errors.log(log);

        Assert.assertEquals(errors.size(), 0);
    }

    @Test(groups = "fast")
    public void testNegativePlanPrice() {
        final MockCatalog catalog = createCatalog();

        final DefaultRecurring recurring = MockRecurring.newRecurring("-1");
        recurring.initialize(catalog);

        final ValidationErrors errors = recurring.validate(catalog, new ValidationErrors());
        errors.log(log);

        Assert.assertEquals(errors.size(), 1);
        Assert.assertTrue(errors.get(0).getDescription().contains("Negative value for price in currency"));
    }
}
