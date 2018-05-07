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
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(groups = "slow")
public class TestOverdueWithTags extends TestOverdueBase {

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
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        return configXml;
    }

    @Test(groups = "slow")
    public void testOverdueStateWith_WRITTEN_OFF() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice nonNullInvoice = invoices.get(1);
        assertTrue(nonNullInvoice.getBalance().compareTo(BigDecimal.ZERO) > 0);

        // Set the WRITTEN_OFF tag
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(nonNullInvoice.getId(), ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);
        assertListenerStatus();

        // Move after what should be OD1 (if invoice had not been written off)
        addDaysAndCheckForCompletion(6);

        // Should still be in clear state because of WRITTEN_OFF tag
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Remove the WRITTEN_OFF tag and verify overdue state is now OD1
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.BLOCK);
        tagUserApi.removeTag(nonNullInvoice.getId(), ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);
        assertListenerStatus();
        checkODState("OD1");
    }

    @Test(groups = "slow")
    public void testNonOverdueAccountWith_OVERDUE_ENFORCEMENT_OFF() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set the OVERDUE_ENFORCEMENT_OFF tag (we set the clear state, hence the blocking event)
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.BLOCK);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.OVERDUE_ENFORCEMENT_OFF.getId(), callContext);
        assertListenerStatus();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // DAY 36 -- RIGHT AFTER OD1
        addDaysAndCheckForCompletion(6);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Now remove OVERDUE_ENFORCEMENT_OFF tag
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.BLOCK);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.OVERDUE_ENFORCEMENT_OFF.getId(), callContext);
        assertListenerStatus();
        checkODState("OD1");
    }

    @Test(groups = "slow")
    public void testOverdueAccountWithOverdueEnforcementOffTag() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // DAY 36 -- RIGHT AFTER OD1
        busHandler.pushExpectedEvent(NextEvent.BLOCK);
        addDaysAndCheckForCompletion(6);
        assertListenerStatus();

        // Account should be in overdue
        checkODState("OD1");

        // Set the OVERDUE_ENFORCEMENT_OFF tag
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.BLOCK);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.OVERDUE_ENFORCEMENT_OFF.getId(), callContext);
        assertListenerStatus();

        // Should now be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Now remove OVERDUE_ENFORCEMENT_OFF tag
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.BLOCK);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.OVERDUE_ENFORCEMENT_OFF.getId(), callContext);
        assertListenerStatus();

        // Account should be back in overdue
        checkODState("OD1");
    }
}
