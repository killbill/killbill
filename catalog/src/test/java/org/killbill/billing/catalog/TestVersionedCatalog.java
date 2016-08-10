/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InvalidConfigException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.platform.api.KillbillService.ServiceException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

public class TestVersionedCatalog extends CatalogTestSuiteNoDB {

    private VersionedCatalog vc;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        vc = loader.loadDefaultCatalog("versionedCatalog");
    }


    @Test(groups = "fast")
    public void testFindPlanWithDates() throws Exception {
        final DateTime dt0 = new DateTime("2010-01-01T00:00:00+00:00");
        final DateTime dt1 = new DateTime("2011-01-01T00:01:00+00:00");
        final DateTime dt2 = new DateTime("2011-02-02T00:01:00+00:00");
        final DateTime dt214 = new DateTime("2011-02-14T00:01:00+00:00");
        final DateTime dt3 = new DateTime("2011-03-03T00:01:00+00:00");

        // New subscription
        try {
            vc.findPlan("pistol-monthly", dt0, dt0);
            Assert.fail("Exception should have been thrown there are no plans for this date");
        } catch (CatalogApiException e) {
            // Expected behaviour
            log.error("Expected exception", e);

        }
        final Plan newSubPlan1 = vc.findPlan("pistol-monthly", dt1, dt1);
        final Plan newSubPlan2 = vc.findPlan("pistol-monthly", dt2, dt2);
        final Plan newSubPlan214 = vc.findPlan("pistol-monthly", dt214, dt214);
        final Plan newSubPlan3 = vc.findPlan("pistol-monthly", dt3, dt3);

        Assert.assertEquals(newSubPlan1.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(newSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Existing subscription

        final Plan exSubPlan2 = vc.findPlan("pistol-monthly", dt2, dt1);
        final Plan exSubPlan214 = vc.findPlan("pistol-monthly", dt214, dt1);
        final Plan exSubPlan3 = vc.findPlan("pistol-monthly", dt3, dt1);

        Assert.assertEquals(exSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(exSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(exSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

    }

    @Test(groups = "fast")
    public void testErrorOnDateTooEarly() {
        final DateTime dt0 = new DateTime("1977-01-01T00:00:00+00:00");
        try {
            vc.findPlan("foo", dt0);
            Assert.fail("Date is too early an exception should have been thrown");
        } catch (CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_CATALOG_FOR_GIVEN_DATE.getCode());
        }
    }
}
