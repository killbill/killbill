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

package org.killbill.billing.invoice.tests.inAdvance;

import static org.killbill.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.model.BillingMode;
import org.killbill.billing.invoice.model.InAdvanceBillingMode;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.tests.ProRationTestBase;

import static org.testng.Assert.assertEquals;

public class TestValidationProRation extends ProRationTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.MONTHLY;
    }

    @Override
    protected BillingMode getBillingMode() {
        return new InAdvanceBillingMode();
    }

    @Test(groups = "fast", expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetStartEnd() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 30);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 3, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(groups = "fast", expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetEndStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 4, 30);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 3, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(groups = "fast", expectedExceptions = InvalidDateSequenceException.class)
    public void testEndTargetStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 3, 30);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(groups = "fast", expectedExceptions = InvalidDateSequenceException.class)
    public void testEndStartTarget() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 30);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(groups = "fast", expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 4, 30);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, targetDate, 15);
    }
}

