/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import static org.testng.Assert.assertEquals;

public class TestOverdue extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per tenant overdue config")
    public void testMultiTenantOverdueConfig() throws Exception {
        uploadTenantOverdueConfig("overdue.xml");

        final String overdueConfig = overdueApi.getOverdueConfigXml(requestOptions);
        Assert.assertNotNull(overdueConfig);
    }

    @Test(groups = "slow", description = "Can retrieve the account overdue status")
    public void testOverdueStatus() throws Exception {
        // Create an account without a payment method
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).isClearState());

        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_FAILED, ExtBusEventType.BLOCKING_STATE, ExtBusEventType.OVERDUE_CHANGE);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).getName(), "OD1");

        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION, ExtBusEventType.BLOCKING_STATE, ExtBusEventType.OVERDUE_CHANGE);
        clock.addDays(10);
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).getName(), "OD2");

        callbackServlet.pushExpectedEvents(ExtBusEventType.BLOCKING_STATE, ExtBusEventType.OVERDUE_CHANGE);
        clock.addDays(10);
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).getName(), "OD3");

        // Post external payments, paying the most recent invoice first: this is to avoid a race condition where
        // a refresh overdue notification kicks in after the first payment, which makes the account goes CLEAR and
        // triggers an AUTO_INVOICE_OFF tag removal (hence adjustment of the other invoices balance).
        final Invoices invoicesForAccount = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        final List<Invoice> mostRecentInvoiceFirst = Ordering.<Invoice>from(new Comparator<Invoice>() {
            @Override
            public int compare(final Invoice invoice1, final Invoice invoice2) {
                return invoice1.getInvoiceDate().compareTo(invoice2.getInvoiceDate());
            }
        }).reverse().sortedCopy(invoicesForAccount);
        for (final Invoice invoice : mostRecentInvoiceFirst) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                final InvoicePayment invoicePayment = new InvoicePayment();
                invoicePayment.setPurchasedAmount(invoice.getAmount());
                invoicePayment.setAccountId(accountJson.getAccountId());
                invoicePayment.setTargetInvoiceId(invoice.getInvoiceId());
                callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_PAYMENT_SUCCESS, ExtBusEventType.PAYMENT_SUCCESS);
                invoiceApi.createInstantPayment(invoice.getInvoiceId(), invoicePayment, true, NULL_PLUGIN_PROPERTIES, requestOptions);
                callbackServlet.assertListenerStatus();
            }
        }

        // Wait a bit for overdue to pick up the payment events...
        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_DELETION, ExtBusEventType.BLOCKING_STATE, ExtBusEventType.OVERDUE_CHANGE);
        callbackServlet.assertListenerStatus();

        // Verify we're in clear state
        Assert.assertTrue(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).isClearState());
    }

    @Test(groups = "slow", description = "Allow overdue condition by control tag defined in overdue config xml file")
    public void testControlTagOverdueConfig() throws Exception {
        uploadTenantOverdueConfig("overdueWithControlTag.xml");

        // Create an account without a payment method and assign a TEST tag
        final Account accountJson = createAccountNoPMBundleAndSubscription();
        callbackServlet.pushExpectedEvent(ExtBusEventType.TAG_CREATION);
        final Tags accountTag = accountApi.createAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(ControlTagType.TEST.getId()), requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.TEST.getId());

        // Create an account without a TEST tag
        final Account accountJsonNoTag = createAccountNoPMBundleAndSubscription();

        // No payment will be triggered as the account doesn't have a payment method
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_FAILED, ExtBusEventType.INVOICE_PAYMENT_FAILED);
        clock.addMonths(1);
        callbackServlet.assertListenerStatus();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        final List<Invoice> invoicesNoTag = accountApi.getInvoicesForAccount(accountJsonNoTag.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoicesNoTag.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).isClearState());
        Assert.assertTrue(accountApi.getOverdueAccount(accountJsonNoTag.getAccountId(), requestOptions).isClearState());

        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
                                           ExtBusEventType.BLOCKING_STATE,
                                           ExtBusEventType.OVERDUE_CHANGE);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // This account is expected to move to OD1 state because it matches with controlTag defined
        Assert.assertEquals(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).getName(), "OD1");
        // This account is not expected to move to OD1 state because it does not match with controlTag defined
        Assert.assertTrue(accountApi.getOverdueAccount(accountJsonNoTag.getAccountId(), requestOptions).isClearState());
    }

    @Test(groups = "slow", description = "Allow overdue condition by exclusion control tag defined in overdue config xml file")
    public void testExclusionControlTagOverdueConfig() throws Exception {
        uploadTenantOverdueConfig("overdueWithExclusionControlTag.xml");

        // Create an account without a payment method and assign a TEST tag
        final Account accountJson = createAccountNoPMBundleAndSubscription();
        callbackServlet.pushExpectedEvent(ExtBusEventType.TAG_CREATION);
        final Tags accountTag = accountApi.createAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(ControlTagType.TEST.getId()), requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.TEST.getId());

        // Create an account without a TEST tag
        final Account accountJsonNoTag = createAccountNoPMBundleAndSubscription();

        // move a month a wait for invoicing
        // No payment will be triggered as the account doesn't have a payment method
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
                                           ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED);
        clock.addMonths(1);
        callbackServlet.assertListenerStatus();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        final List<Invoice> invoicesNoTag = accountApi.getInvoicesForAccount(accountJsonNoTag.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoicesNoTag.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).isClearState());
        Assert.assertTrue(accountApi.getOverdueAccount(accountJsonNoTag.getAccountId(), requestOptions).isClearState());

        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
                                           ExtBusEventType.BLOCKING_STATE,
                                           ExtBusEventType.OVERDUE_CHANGE);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // This account is not expected to move to OD1 state because it does not match with exclusion controlTag defined
        Assert.assertTrue(accountApi.getOverdueAccount(accountJson.getAccountId(), requestOptions).isClearState());
        // This account is expected to move to OD1 state because it matches with exclusion controlTag defined
        Assert.assertEquals(accountApi.getOverdueAccount(accountJsonNoTag.getAccountId(), requestOptions).getName(), "OD1");
    }
}
