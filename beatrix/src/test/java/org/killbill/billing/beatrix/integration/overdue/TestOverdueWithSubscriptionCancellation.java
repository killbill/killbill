/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Test(groups = "slow")
public class TestOverdueWithSubscriptionCancellation extends TestOverdueBase {

    @Override
    public String getOverdueConfig() {
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>5</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>5</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <subscriptionCancellationPolicy>IMMEDIATE</subscriptionCancellationPolicy>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        return configXml;
    }

    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testCheckSubscriptionCancellation() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement addOn1 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Holster", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final DefaultEntitlement addOn2 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Holster", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Cancel addOn1 one day after
        clock.addDays(1);
        cancelEntitlementAndCheckForCompletion(addOn1, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(29, NextEvent.PHASE,  NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);


        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);


        // DAY 36 -- RIGHT AFTER OD1 (two block events, for the cancellation and the OD1 state)
        // One BLOCK event is for the overdue state transition
        // The 2 other BLOCK are for the entitlement blocking states for both base plan and addOn2
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE);

        // Should be in OD1
        checkODState("OD1");

        final SubscriptionBase cancelledBaseSubscription = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseSubscription.getState() == EntitlementState.CANCELLED);

        final SubscriptionBase cancelledAddon1= ((DefaultEntitlement) entitlementApi.getEntitlementForId(addOn1.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledAddon1.getState() == EntitlementState.CANCELLED);
    }

    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testCheckSubscriptionCancellationWithMultipleBundles() throws Exception {
        // 2012-05-01T00:03:53.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement baseEntitlement2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final SubscriptionBundle bundle2 = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement2.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement baseEntitlement3 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey3", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final SubscriptionBundle bundle3 = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement2.getId(), new LocalDate(2012, 5, 1), callContext);

        // Cancel bundle 2 one day after (2012-05-02)
        clock.addDays(1);
        cancelEntitlementAndCheckForCompletion(baseEntitlement2, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE);
        final SubscriptionBase cancelledBaseSubscription2 = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement2.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseSubscription2.getState() == EntitlementState.CANCELLED);

        // DAY 30 have to get out of trial before first payment (2012-05-31)
        addDaysAndCheckForCompletion(29, NextEvent.PHASE,  NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        invoiceChecker.checkInvoice(account.getId(),
                                    4,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // DAY 36 (2012-06-06)-- RIGHT AFTER OD1 (two block events, for the cancellation and the OD1 state)
        // One BLOCK event is for the overdue state transition
        // The 2 other BLOCK are for the entitlement blocking states for both baseEntitlement and baseEntitlement3
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE);

        // Should be in OD1
        checkODState("OD1");

        final SubscriptionBase cancelledBaseSubscription = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseSubscription.getState() == EntitlementState.CANCELLED);

        final SubscriptionBase cancelledBaseEntitlement3 = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement3.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseEntitlement3.getState() == EntitlementState.CANCELLED);
    }
}
