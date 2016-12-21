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

package org.killbill.billing.catalog;

import java.util.Date;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.xmlloader.ValidationErrors;

public class TestPlan extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testDateValidation() {
        final StandaloneCatalog c = new MockCatalog();
        c.setSupportedCurrencies(new Currency[]{Currency.GBP, Currency.EUR, Currency.USD, Currency.BRL, Currency.MXN});
        final DefaultPlan p1 = MockPlan.createBicycleTrialEvergreen1USD();
        p1.setPlansAllowedInBundle(-1);
        p1.setEffectiveDateForExistingSubscriptions(new Date((new Date().getTime()) - (1000 * 60 * 60 * 24)));
        final ValidationErrors errors = p1.validate(c, new ValidationErrors());
        Assert.assertEquals(errors.size(), 3);
        errors.log(log);
    }

    @Test(groups = "fast")
    public void testDataCalc() {
        final DefaultPlan p0 = MockPlan.createBicycleTrialEvergreen1USD();

        final DefaultPlan p1 = MockPlan.createBicycleTrialEvergreen1USD(100);

        final DefaultPlan p2 = MockPlan.createBicycleNoTrialEvergreen1USD();

        final DateTime requestedDate = new DateTime();
        Assert.assertEquals(p0.dateOfFirstRecurringNonZeroCharge(requestedDate, null).compareTo(requestedDate.plusDays(30)), 0);
        Assert.assertEquals(p1.dateOfFirstRecurringNonZeroCharge(requestedDate, null).compareTo(requestedDate.plusDays(100)), 0);
        Assert.assertEquals(p2.dateOfFirstRecurringNonZeroCharge(requestedDate, null).compareTo(requestedDate.plusDays(0)), 0);
    }
}
