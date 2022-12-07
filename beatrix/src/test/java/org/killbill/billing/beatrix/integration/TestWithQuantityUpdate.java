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
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.annotations.Test;

public class TestWithQuantityUpdate extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testQuantityBasic() throws Exception {

        final LocalDate today = new LocalDate(2022, 11, 27);
        clock.setDay(today);

        // We chose 20 (> local BCD = 15) to ensure test fails if local bcd is not correct taken into account
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(27));

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.QUANTITY_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("knife-monthly-notrial");
        final UUID subId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, 7, null, null), null, null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 11, 27), new LocalDate(2022, 12, 27), InvoiceItemType.RECURRING, new BigDecimal("209.65")));

        // Let some time pass and update the quantity to match next invoice date (no proration)
        clock.addDays(15);
        final LocalDate updateDate = new LocalDate(2022, 12, 27);
        final Entitlement ent = entitlementApi.getEntitlementForId(subId, false, callContext);

        ent.updateQuantity(10, updateDate, callContext);

        busHandler.pushExpectedEvents(NextEvent.QUANTITY_CHANGE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(15);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2022, 12, 27), new LocalDate(2023, 1, 27), InvoiceItemType.RECURRING, new BigDecimal("299.50")));

    }

}
