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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;

import static org.testng.Assert.assertNotNull;

public class TestBillingAlignment extends TestIntegrationBase {

    // TODO test fails as it should not create a proration when the chnage to annual occurs. Instaed we should restart from the data of the chnage
    // since we have as a catalog rule:
    // <billingAlignmentCase>
    // <billingPeriod>ANNUAL</billingPeriod>
    // <alignment>SUBSCRIPTION</alignment>
    // </billingAlignmentCase>
    //
    @Test(groups = "slow", enabled = false)
    public void testTransitonAccountBAToSubscriptionBA() throws Exception {

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        // (Start with monthly that has a 'Account' billing alignment
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        // GET OUT TRIAL
        addDaysAndCheckForCompletion(33, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);

        //
        // Change plan to annual that has been configured to have a 'SubscriptionBase' billing alignment
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Shotgun", BillingPeriod.ANNUAL, null, NextEvent.CHANGE, NextEvent.INVOICE);
    }
}
