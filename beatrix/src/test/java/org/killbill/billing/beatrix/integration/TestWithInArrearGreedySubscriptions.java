/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.InvoiceConfig.InArrearMode;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithInArrearGreedySubscriptions extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        // Reuse catalog from testWithInArrearSubscriptions as we are doind in-arrear (just a different behavior)
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testWithInArrearGreedySubscriptions");
        return super.getConfigSource(null, allExtraProperties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        invoiceConfig.setInArrearMode(InArrearMode.GREEDY);

    }

    @Test(groups = "slow")
    public void testWithBillRun_A() throws Exception {
        final DateTime initialCreationDate = new DateTime(2022, 1, 13, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BCD_CHANGE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 13, null, null, null), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        // 2022-02-01
        clock.addDays(19);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 1, 13), new LocalDate(2022, 2, 13), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2022-02-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(12);
        assertListenerStatus();

        // 2022-03-01
        clock.addDays(16);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 2, 13), new LocalDate(2022, 3, 13), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2022-03-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(12);
        assertListenerStatus();

        // Cancel 2022-03-13
        // Cancel EOT (no invoice expected)
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.of(), callContext);
        assertListenerStatus();

        // 2022-04-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        for (int i = 0; i < 3; i++) {
            clock.addMonths(1);
            assertListenerStatus();
        }

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testWithBillRun_B() throws Exception {
        final DateTime initialCreationDate = new DateTime(2022, 1, 13, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BCD_CHANGE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 13, null, null, null), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        // 2022-02-01
        clock.addDays(19);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 1, 13), new LocalDate(2022, 2, 13), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2022-02-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(12);
        assertListenerStatus();

        // 2022-03-01
        clock.addDays(16);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 2, 13), new LocalDate(2022, 3, 13), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // Cancel 2022-03-11
        clock.addDays(10);
        // Cancel prior EOT
        // (REPAIR invoice expected, as if we were billing in-advance)
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 11), new LocalDate(2022, 3, 13), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-7.14")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 11), new LocalDate(2022, 3, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("7.14")));

        // 2022-03-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(2);
        assertListenerStatus();

        // 2022-04-13
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        for (int i = 0; i < 3; i++) {
            clock.addMonths(1);
            assertListenerStatus();
        }

        checkNoMoreInvoiceToGenerate(account);
    }

}