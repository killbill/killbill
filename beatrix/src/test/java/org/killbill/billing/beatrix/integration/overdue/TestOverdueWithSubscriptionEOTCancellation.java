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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(groups = "slow")
public class TestOverdueWithSubscriptionEOTCancellation extends TestOverdueBase {

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
                                 "           <subscriptionCancellationPolicy>END_OF_TERM</subscriptionCancellationPolicy>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        return configXml;
    }

    @Test(groups = "slow")
    public void testCheckSubscriptionEOTCancellation() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        final DefaultEntitlement addOn1 = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Holster", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);
        invoiceChecker.checkChargedThroughDate(addOn1.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // We expect one event for OD1 transition and for for each entitlement cancellation (entitlement cancellation is immediate)
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK);

        // Should be in OD1
        checkODState("OD1");

        final Subscription cancelledBaseSubscription = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), callContext);
        assertTrue(cancelledBaseSubscription.getState() == EntitlementState.CANCELLED);
        assertEquals(cancelledBaseSubscription.getEffectiveEndDate(), new LocalDate(2012, 6, 05));
        assertEquals(cancelledBaseSubscription.getBillingEndDate(), new LocalDate(2012, 6, 30));

        final Subscription cancelledAddon1 = subscriptionApi.getSubscriptionForEntitlementId(addOn1.getId(), callContext);
        assertTrue(cancelledAddon1.getState() == EntitlementState.CANCELLED);
        assertEquals(cancelledAddon1.getEffectiveEndDate(), new LocalDate(2012, 6, 05));
        assertEquals(cancelledAddon1.getBillingEndDate(), new LocalDate(2012, 6, 30));

        // Payment Retry on the 2012-6-8
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        assertListenerStatus();

        // Payment Retry on the 2012-6-16
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        assertListenerStatus();

        // Payment Retry on the 2012-6-24
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        assertListenerStatus();

        // 2012-6-30
        addDaysAndCheckForCompletion(6, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.CANCEL, NextEvent.CANCEL);
        assertListenerStatus();
    }

}
