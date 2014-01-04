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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.api.SubscriptionBundle;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.subscription.api.SubscriptionBase;

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

    @Test(groups = "slow")
    public void testCheckSubscriptionCancellation() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement addOn1 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Holster", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);

        final DefaultEntitlement addOn2 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Holster", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);

        // Cancel addOn1 one day after
        clock.addDays(1);
        cancelEntitlementAndCheckForCompletion(addOn1, clock.getUTCNow(), NextEvent.BLOCK, NextEvent.CANCEL);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(29, NextEvent.PHASE,  NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);


        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);


        // DAY 36 -- RIGHT AFTER OD1 (two block events, for the cancellation and the OD1 state)
        // One BLOCK event is for the overdue state transition
        // The 2 other BLOCK are for the entitlement blocking states for both base plan and addOn2
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE_ADJUSTMENT);

        // Should be in OD1
        checkODState("OD1");

        final SubscriptionBase cancelledBaseSubscription = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseSubscription.getState() == EntitlementState.CANCELLED);

        final SubscriptionBase cancelledAddon1= ((DefaultEntitlement) entitlementApi.getEntitlementForId(addOn1.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledAddon1.getState() == EntitlementState.CANCELLED);
    }


    @Test(groups = "slow")
    public void testCheckSubscriptionCancellationWithMultipleBundles() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement baseEntitlement2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        final SubscriptionBundle bundle2 = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        final DefaultEntitlement baseEntitlement3 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey3", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        final SubscriptionBundle bundle3 = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        // Cancel addOn1 one day after
        clock.addDays(1);
        cancelEntitlementAndCheckForCompletion(baseEntitlement2, clock.getUTCNow(), NextEvent.BLOCK, NextEvent.CANCEL);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(29, NextEvent.PHASE,  NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);


        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);


        // DAY 36 -- RIGHT AFTER OD1 (two block events, for the cancellation and the OD1 state)
        // One BLOCK event is for the overdue state transition
        // The 2 other BLOCK are for the entitlement blocking states for both base plan and baseEntitlement3
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE_ADJUSTMENT);

        // Should be in OD1
        checkODState("OD1");

        final SubscriptionBase cancelledBaseSubscription = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledBaseSubscription.getState() == EntitlementState.CANCELLED);

        final SubscriptionBase cancelledbaseEntitlement3 = ((DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlement3.getId(), callContext)).getSubscriptionBase();
        assertTrue(cancelledbaseEntitlement3.getState() == EntitlementState.CANCELLED);
    }

}
