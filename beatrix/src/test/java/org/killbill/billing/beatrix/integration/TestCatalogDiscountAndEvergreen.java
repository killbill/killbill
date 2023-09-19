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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestCatalogDiscountAndEvergreen extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogDiscountAndEvergreen");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testCatalogDiscountAndEvergreenUTC() throws Exception {

        // Set clock to 2023-02-28T3:47:56
        final DateTime initialDateTime = new DateTime(2023, 2, 28,3,47,56);
        clock.setTime(initialDateTime);

        final AccountData accountData = getAccountData(28);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        assertEquals(account.getReferenceTime().compareTo(initialDateTime), 0); //Reference time set to 2023-02-28T3:47:56
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //standard-monthly has a 3-month discount phase followed by an evergreen phase

        //CREATE SUBSCRIPTION
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly");
        final UUID subscriptionId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        subscriptionChecker.checkSubscriptionCreated(subscriptionId, internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 2, 28), new LocalDate(2023, 3, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-03-28
        clock.setTime(new DateTime(2023, 3, 28,3,47,56));
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 28), new LocalDate(2023, 4, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-04-28
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 28), new LocalDate(2023, 5, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-05-28 - end of discount phase
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 28), new LocalDate(2023, 6, 28), InvoiceItemType.RECURRING, new BigDecimal("24.95")));
    }


    @Test(groups = "slow")
    public void testCatalogDiscountAndEvergreen() throws Exception {

        // Set clock to 2023-03-01T3:47
        final DateTime initialDateTime = new DateTime(2023, 3, 1,3,47,56);
        clock.setTime(initialDateTime);

        final AccountData accountData = getAccountData(28, DateTimeZone.forID("America/New_York"));
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        assertEquals(account.getReferenceTime().compareTo(initialDateTime), 0); //Reference time set to 2023-03-01T3:47
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // CREATE SUBSCRIPTION
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly");
        final UUID subscriptionId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        subscriptionChecker.checkSubscriptionCreated(subscriptionId, internalCallContext);
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, false, callContext);
        DateTime startDate = subscription.getBillingStartDate().toDateTime(DateTimeZone.forID("America/New_York"));
        assertEquals(startDate.toLocalDate().compareTo(new LocalDate("2023-02-28")), 0); //Verify that startDate is 2023-02-28 in the user's timezone
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 2, 28), new LocalDate(2023, 3, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-04-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 28), new LocalDate(2023, 4, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-05-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 28), new LocalDate(2023, 5, 28), InvoiceItemType.RECURRING, new BigDecimal("4.95")));

        //2023-06-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 28), new LocalDate(2023, 6, 28), InvoiceItemType.RECURRING, new BigDecimal("24.95")));

        //2023-07-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 5, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 28), new LocalDate(2023, 7, 28), InvoiceItemType.RECURRING, new BigDecimal("24.95")));

    }

}
