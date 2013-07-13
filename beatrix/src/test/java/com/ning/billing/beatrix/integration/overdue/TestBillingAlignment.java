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

package com.ning.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.TestIntegrationBase;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.InvoiceItemType;

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

        final SubscriptionBundle bundle = subscriptionUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        // (Start with monthly that has a 'Account' billing alignment
        //
        final Subscription bpSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        // GET OUT TRIAL
        addDaysAndCheckForCompletion(33, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);

        //
        // Change plan to annual that has been configured to have a 'Subscription' billing alignment
        changeSubscriptionAndCheckForCompletion(bpSubscription, "Shotgun", BillingPeriod.ANNUAL, NextEvent.CHANGE, NextEvent.INVOICE);


        /*

        | 64e17f77-fcdd-4c87-8543-1a64d957460c | FIXED      | 2012-04-01 | NULL       |    0.0000 |      NULL | shotgun-monthly |
        | 07924bfa-cc9b-46dc-ad22-a9a39830a128 | RECURRING  | 2012-05-01 | 2012-06-01 |  249.9500 |  249.9500 | shotgun-monthly |
        | 92c1e86b-284a-4d33-a920-3cbc6e05f7e6 | RECURRING  | 2012-05-01 | 2012-05-04 |   24.2000 |  249.9500 | shotgun-monthly |
        | 92c1e86b-284a-4d33-a920-3cbc6e05f7e6 | RECURRING  | 2012-05-04 | 2012-06-01 |  183.6000 | 2399.9500 | shotgun-annual  |
        | 07924bfa-cc9b-46dc-ad22-a9a39830a128 | REPAIR_ADJ | 2012-05-01 | 2012-06-01 | -249.9500 |      NULL | NULL            |
        | 07924bfa-cc9b-46dc-ad22-a9a39830a128 | CBA_ADJ    | 2012-05-04 | 2012-05-04 |  249.9500 |      NULL | NULL            |
        | 92c1e86b-284a-4d33-a920-3cbc6e05f7e6 | CBA_ADJ    | 2012-05-04 | 2012-05-04 | -207.8000 |      NULL | NULL            |
         */
    }
}
