/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestInArrearWithCatalogVersions extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource() {
        return super.getConfigSource(null, ImmutableMap.of("org.killbill.catalog.uri", "catalogs/testInArrearWithCatalogVersions"));
    }

    @Test(groups = "slow")
    public void testWithNoUsageInPeriodAndOldUsage() throws Exception {
        // 30 days month
        clock.setDay(new LocalDate(2016, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("electricity-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        setUsage(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 1), 143L, callContext);
        setUsage(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 18), 57L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.USAGE, new BigDecimal("300.00")));

        setUsage(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 2), 100L, callContext); // -> Uses v1 : $150

        // Catalog change with new price on 2016-05-08
        // Schedule CHANGE_PLAN on 2016-05-09
        final Entitlement bp = entitlementApi.getEntitlementForId(entitlementId, callContext);

        bp.changePlanWithDate(new DefaultEntitlementSpecifier(spec), new LocalDate("2016-05-09"), null, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 9), InvoiceItemType.USAGE, new BigDecimal("150.00")));


        setUsage(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 10), 100L, callContext); // -> Uses v2 : $250


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(23);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 9), new LocalDate(2016, 6, 1), InvoiceItemType.USAGE, new BigDecimal("250.00")));


    }
}