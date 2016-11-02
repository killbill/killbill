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

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Ordering;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;

public class TestOverdue extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per tenant overdue config")
    public void testMultiTenantOverdueConfig() throws Exception {
        final String overdueConfigPath = Resources.getResource("overdue.xml").getPath();
        killBillClient.uploadXMLOverdueConfig(overdueConfigPath, requestOptions);

        final String overdueConfig = killBillClient.getXMLOverdueConfig(requestOptions);
        Assert.assertNotNull(overdueConfig);
    }

    @Test(groups = "slow", description = "Can retrieve the account overdue status")
    public void testOverdueStatus() throws Exception {
        // Create an account without a payment method
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getIsClearState());

        clock.addDays(30);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getName(), "OD1");

        clock.addDays(10);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getName(), "OD2");

        clock.addDays(10);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getName(), "OD3");

        // Post external payments, paying the most recent invoice first: this is to avoid a race condition where
        // a refresh overdue notification kicks in after the first payment, which makes the account goes CLEAR and
        // triggers an AUTO_INVOICE_OFF tag removal (hence adjustment of the other invoices balance).
        final Invoices invoicesForAccount = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
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
                killBillClient.createInvoicePayment(invoicePayment, true, requestOptions);
            }
        }

        // Wait a bit for overdue to pick up the payment events...
        crappyWaitForLackOfProperSynchonization();

        // Verify we're in clear state
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getIsClearState());
    }

    @Test(groups = "slow", description = "Allow overdue condition by control tag defined in overdue config xml file")
    public void testControlTagOverdueConfig() throws Exception {
        final String overdueConfigPath = Resources.getResource("overdueWithControlTag.xml").getPath();
        killBillClient.uploadXMLOverdueConfig(overdueConfigPath, requestOptions);

        // Create an account without a payment method and assign a TEST tag
        final Account accountJson = createAccountNoPMBundleAndSubscription();
        final Tags accountTag = killBillClient.createAccountTag(accountJson.getAccountId(), ControlTagType.TEST.getId(), requestOptions);
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.TEST.getId());

        // Create an account without a TEST tag
        final Account accountJsonNoTag = createAccountNoPMBundleAndSubscription();

        // No payment will be triggered as the account doesn't have a payment method
        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        final List<Invoice> invoicesNoTag = killBillClient.getInvoicesForAccount(accountJsonNoTag.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoicesNoTag.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getIsClearState());
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJsonNoTag.getAccountId(), requestOptions).getIsClearState());

        clock.addDays(30);
        crappyWaitForLackOfProperSynchonization();

        // This account is expected to move to OD1 state because it matches with controlTag defined
        Assert.assertEquals(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getName(), "OD1");
        // This account is not expected to move to OD1 state because it does not match with controlTag defined
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJsonNoTag.getAccountId(), requestOptions).getIsClearState());
    }

    @Test(groups = "slow", description = "Allow overdue condition by exclusion control tag defined in overdue config xml file")
    public void testExclusionControlTagOverdueConfig() throws Exception {
        final String overdueConfigPath = Resources.getResource("overdueWithExclusionControlTag.xml").getPath();
        killBillClient.uploadXMLOverdueConfig(overdueConfigPath, requestOptions);

        // Create an account without a payment method and assign a TEST tag
        final Account accountJson = createAccountNoPMBundleAndSubscription();
        final Tags accountTag = killBillClient.createAccountTag(accountJson.getAccountId(), ControlTagType.TEST.getId(), requestOptions);
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.TEST.getId());

        // Create an account without a TEST tag
        final Account accountJsonNoTag = createAccountNoPMBundleAndSubscription();

        // move a month a wait for invoicing
        // No payment will be triggered as the account doesn't have a payment method
        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        // Get the invoices
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);

        final List<Invoice> invoicesNoTag = killBillClient.getInvoicesForAccount(accountJsonNoTag.getAccountId(), requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoicesNoTag.size(), 2);

        // We're still clear - see the configuration
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getIsClearState());
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJsonNoTag.getAccountId(), requestOptions).getIsClearState());

        clock.addDays(30);
        crappyWaitForLackOfProperSynchonization();

        // This account is not expected to move to OD1 state because it does not match with exclusion controlTag defined
        Assert.assertTrue(killBillClient.getOverdueStateForAccount(accountJson.getAccountId(), requestOptions).getIsClearState());
        // This account is expected to move to OD1 state because it matches with exclusion controlTag defined
        Assert.assertEquals(killBillClient.getOverdueStateForAccount(accountJsonNoTag.getAccountId(), requestOptions).getName(), "OD1");
    }
}
