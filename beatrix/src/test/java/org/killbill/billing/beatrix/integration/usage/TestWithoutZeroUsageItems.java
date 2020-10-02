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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;

public class TestWithoutZeroUsageItems extends TestIntegrationBase {

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        invoiceConfig.setZeroAmountUsageDisabled(true);
    }

    @Test(groups = "slow")
    public void testCapacityWithBlankPeriods() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertNull(bpSubscription.getSubscriptionBase().getChargedThroughDate());

        // Record usage for first month
        recordUsageData(bpSubscription.getId(), "xxx-1", "stones", new LocalDate(2012, 4, 5), 85L, callContext);
        recordUsageData(bpSubscription.getId(), "xxx-2", "stones", new LocalDate(2012, 4, 15), 150L, callContext);

        // 2012-5-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("xxx-1", "xxx-2"), internalCallContext);

        final DateTime firstExpectedCTD = account.getReferenceTime().withMonthOfYear(5).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(firstExpectedCTD), 0);

        // Beause we don't have $0 usage item we see a null invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();


        final DateTime secondExpectedCTD = account.getReferenceTime().withMonthOfYear(6).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(secondExpectedCTD), 0);

        // Record usage for third month (verify invoicing resumes)
        recordUsageData(bpSubscription.getId(), "xxx-3", "stones", new LocalDate(2012, 6, 5), 25L, callContext);
        recordUsageData(bpSubscription.getId(), "xxx-4", "stones", new LocalDate(2012, 6, 15), 50L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("100")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("xxx-3", "xxx-4"), internalCallContext);

        final DateTime thirdExpectedCTD = account.getReferenceTime().withMonthOfYear(7).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(thirdExpectedCTD), 0);
    }

}
