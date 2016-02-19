/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestBillingAlignment extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testTransitionAccountBAToSubscriptionBA() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        // Set the BCD to the 25th
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        // (Start with monthly that has an 'Account' billing alignment)
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(bpEntitlement.getId(), new LocalDate(2012, 4, 1), callContext);

        // GET OUT TRIAL (moving clock to 2012-05-04)
        addDaysAndCheckForCompletion(33, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 25), InvoiceItemType.RECURRING, new BigDecimal("199.96")));
        invoiceChecker.checkChargedThroughDate(bpEntitlement.getId(), new LocalDate(2012, 5, 25), callContext);

        // Change plan to annual that has been configured to have a 'Subscription' billing alignment
        final DefaultEntitlement changedBpEntitlement = changeEntitlementAndCheckForCompletion(bpEntitlement, "Shotgun", BillingPeriod.ANNUAL, null, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(),
                                    3,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 4), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2380.22")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 4), new LocalDate(2012, 5, 25), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-174.97")));
        invoiceChecker.checkChargedThroughDate(bpEntitlement.getId(), new LocalDate(2013, 5, 1), callContext);

        Assert.assertEquals(changedBpEntitlement.getSubscriptionBase().getAllTransitions().size(), 3);

        final SubscriptionBaseTransition trial = changedBpEntitlement.getSubscriptionBase().getAllTransitions().get(0);
        Assert.assertEquals(trial.getEffectiveTransitionTime().toLocalDate().compareTo(new LocalDate(2012, 4, 1)), 0);
        Assert.assertEquals(trial.getNextPhase().getName(), "shotgun-monthly-trial");

        final SubscriptionBaseTransition smEvergreen = changedBpEntitlement.getSubscriptionBase().getAllTransitions().get(1);
        Assert.assertEquals(smEvergreen.getEffectiveTransitionTime().toLocalDate().compareTo(new LocalDate(2012, 5, 1)), 0);
        Assert.assertEquals(smEvergreen.getNextPhase().getName(), "shotgun-monthly-evergreen");

        final SubscriptionBaseTransition saEvergreen = changedBpEntitlement.getSubscriptionBase().getAllTransitions().get(2);
        // Verify the IMMEDIATE policy
        Assert.assertEquals(saEvergreen.getEffectiveTransitionTime().toLocalDate().compareTo(new LocalDate(2012, 5, 4)), 0);
        // Verify the START_OF_SUBSCRIPTION alignment (both plans have the same 30 days trial)
        Assert.assertEquals(saEvergreen.getNextPhase().getName(), "shotgun-annual-evergreen");
    }
}
