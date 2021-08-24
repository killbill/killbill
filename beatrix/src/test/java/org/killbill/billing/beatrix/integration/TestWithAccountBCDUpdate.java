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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.features.KillbillFeatures;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithAccountBCDUpdate extends TestIntegrationBase {

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        this.killbillFeatures = Mockito.spy(KillbillFeatures.class);
        Mockito.when(killbillFeatures.allowAccountBCDUpdate()).thenReturn(true);

        super.beforeClass();
    }

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        allExtraProperties.put(KillbillFeatures.PROP_FEATURE_INVOICE_OPTIMIZATION, "true");
        allExtraProperties.put("org.killbill.cache.disabled", "account-bcd");
        return getConfigSource(null, allExtraProperties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();

        invoiceConfig.setMaxInvoiceLimit(new Period(InvoiceConfig.DEFAULT_NULL_PERIOD));
    }

    @Test(groups = "slow")
    public void testAccountBCDChangeWithNoOptimization() throws Exception {
        final Account account = setupScenario();

        // 2016-7-17: BCD alignment
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        // Regular invoice (17th -> 17th)
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 17), new LocalDate(2016, 8, 17), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 17), new LocalDate(2016, 7, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("-25")));
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-20: nothing happens
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(3);
        assertListenerStatus();

        // 2016-8-17
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(28);
        assertListenerStatus();

        // Regular invoice (17th -> 17th)
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 8, 17), new LocalDate(2016, 9, 17), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();
    }

    private Account setupScenario() throws Exception {
        final DateTime initialDate = new DateTime(2016, 4, 17, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(17));
        assertNotNull(account);

        assertEquals(accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal(), (Integer) 17);

        // BP creation
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 17), null, InvoiceItemType.FIXED, BigDecimal.ZERO));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-4-20: (BP still in TRIAL)
        clock.addDays(3);

        // Set next BCD to be the 20
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        accountUserApi.updateAccount(account.getId(), new DefaultMutableAccountData(null, null, null, 0, null, null, null, 20, null, null, null, null, null, null, null, null, null, null, null, null, null, false), callContext);
        assertListenerStatus();
        assertEquals(accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal(), (Integer) 20);

        // 2016-5-17: PHASE change
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        // Leading pro-ration to the new BCD
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 17), new LocalDate(2016, 5, 20), InvoiceItemType.RECURRING, new BigDecimal("25")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-5-20: BCD alignment
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        // Regular invoice (20th -> 20th)
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 20), new LocalDate(2016, 6, 20), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-6-20
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Regular invoice (20th -> 20th)
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 20), new LocalDate(2016, 7, 20), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // Move the BCD back to the 17 -- the 3 days between 2016-07-17 and 2016-07-20 are repaired
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        accountUserApi.updateAccount(account.getId(), new DefaultMutableAccountData(null, null, null, 0, null, null, null, 17, null, null, null, null, null, null, null, null, null, null, null, null, null, false), callContext);
        assertListenerStatus();
        assertEquals(accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal(), (Integer) 17);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 17), new LocalDate(2016, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-25")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 20), new LocalDate(2016, 6, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("25")));
        invoiceChecker.checkInvoice(invoices.get(4).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        return account;
    }
}
