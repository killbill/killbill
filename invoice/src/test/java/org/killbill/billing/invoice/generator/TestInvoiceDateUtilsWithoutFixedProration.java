/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoiceDateUtilsWithoutFixedProration extends InvoiceTestSuiteNoDB {

    @Test(groups = "fast")
    public void testProRationAfterLastBillingCycleDate() throws Exception {
        LocalDate endDate = new LocalDate("2023-06-15");
        LocalDate previousBillThroughDate = new LocalDate("2023-05-23");
        BigDecimal proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.741935484"));

        endDate = new LocalDate("2023-07-15");
        previousBillThroughDate = new LocalDate("2023-06-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb
        endDate = new LocalDate("2023-03-15");
        previousBillThroughDate = new LocalDate("2023-02-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.714285714"));

        //Feb with leap year
        endDate = new LocalDate("2024-03-15");
        previousBillThroughDate = new LocalDate("2024-02-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.724137931"));

    }

    @Test(groups = "fast")
    public void testProRationBeforeFirstBillingPeriod() throws Exception {
        LocalDate startDate = new LocalDate("2023-05-23");
        LocalDate nextBillingCycleDate = new LocalDate("2023-06-15");
        BigDecimal proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.741935484"));

        startDate = new LocalDate("2023-06-23");
        nextBillingCycleDate = new LocalDate("2023-07-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb
        startDate = new LocalDate("2023-02-23");
        nextBillingCycleDate = new LocalDate("2023-03-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.714285714"));

        //Feb with leap year
        startDate = new LocalDate("2024-02-23");
        nextBillingCycleDate = new LocalDate("2024-03-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.724137931"));

    }
}
