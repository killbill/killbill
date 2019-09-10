/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertNotNull;

public class TestWithInvoiceHardening extends TestIntegrationBase {

    @Inject
    protected InvoiceDao invoiceDao;

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1205")
    public void testFor1205() throws Exception {

        final DateTimeZone testTimeZone = DateTimeZone.UTC;

        final DateTime initialDate = new DateTime(2019, 4, 27, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2019-05-03
        clock.addDays(6);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState1 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_5_3", "SERVICE_5_3", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 27), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-23.96")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 03), InvoiceItemType.CBA_ADJ, new BigDecimal("23.96")));

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState2 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_4_27", "SERVICE_4_27", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState2, new LocalDate(2019, 4, 27), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 03), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.99")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 03), InvoiceItemType.CBA_ADJ, new BigDecimal("5.99")));

        /*
        // 2019-05-17
        clock.addDays(14);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState3 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_5_17", "SERVICE_5_17", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState3, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState4 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "UNBLOCK_5_3", "SERVICE_5_3", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState4, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState5 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "UNBLOCK_4_27", "SERVICE_4_27", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState5, new LocalDate(2019, 4, 27), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 17), InvoiceItemType.RECURRING, new BigDecimal("9.98")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("5.99")));
                                    
         */

    }
}
