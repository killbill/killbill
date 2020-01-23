/*
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertNotNull;

public class TestWithInArrearSubscriptions extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testWithInArrearSubscriptions");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testWithCancelation() throws Exception {
        final DateTime initialCreationDate = new DateTime(2020, 1, 1, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // Cancel 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        for (int i = 0; i < 7; i++) {
            clock.addMonths(1);
            assertListenerStatus();
        }

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testWithPauseResume() throws Exception {
        final DateTime initialDate = new DateTime(2020, 1, 1, 0, 3, 42, 0, testTimeZone);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-01-15
        clock.addDays(14);

        // Pause subscription. System will invoice for 2020-01-01 -> 2020-01-15
        DefaultEntitlement entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(entitlementId, callContext);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.pause(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 1, 15), InvoiceItemType.RECURRING, new BigDecimal("45.16")));

        // 2020-01-20
        clock.addDays(5);

        // Resume subscription.  System will invoice for remaining 2020-01-20 -> 2020-02-1
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlementApi.resume(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(12);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 20), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("38.71")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }



}
