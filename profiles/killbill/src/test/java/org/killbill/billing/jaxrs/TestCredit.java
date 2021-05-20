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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.InvoiceItems;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestCredit extends TestJaxrsBase {

    Account accountJson;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }

        accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
    }

    @Test(groups = "slow", description = "Can add a credit to an existing invoice")
    public void testAddCreditToInvoice() throws Exception {
        final DateTime effectiveDate = clock.getUTCNow();
        final BigDecimal creditAmount = BigDecimal.ONE;
        final InvoiceItem credit = new InvoiceItem();
        credit.setAccountId(accountJson.getAccountId());
        credit.setAmount(creditAmount);
        credit.setDescription("description");
        credit.setQuantity(5);
        credit.setRate(BigDecimal.TEN);
        credit.setItemDetails("itemDetails");

        final InvoiceItems credits = new InvoiceItems();
        credits.add(credit);
        InvoiceItems createdCredits = creditApi.createCredits(credits, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        final UUID invoiceId = createdCredits.get(0).getInvoiceId();
        credit.setInvoiceId(invoiceId);
        createdCredits = creditApi.createCredits(credits, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        // We can't just compare the object via .equals() due e.g. to the invoice id
        assertEquals(createdCredits.get(0).getAccountId(), accountJson.getAccountId());
        assertEquals(createdCredits.get(0).getInvoiceId(), invoiceId);
        assertEquals(createdCredits.get(0).getAmount().compareTo(creditAmount), 0);
        assertEquals(createdCredits.get(0).getStartDate().compareTo(effectiveDate.toLocalDate()), 0);
        assertEquals(createdCredits.get(0).getDescription().compareTo("description"), 0);
        assertEquals(createdCredits.get(0).getQuantity().compareTo(5), 0);
        assertEquals(createdCredits.get(0).getRate().compareTo(BigDecimal.TEN), 0);
        assertEquals(createdCredits.get(0).getItemDetails().compareTo("itemDetails"), 0);
    }

    @Test(groups = "slow", description = "Can add a credit to an existing account",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = ".*it is already in COMMITTED status")
    public void testAddCreditToCommittedInvoice() throws Exception {
        final Invoice invoice = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).get(1);

        final BigDecimal creditAmount = BigDecimal.ONE;
        final InvoiceItem credit = new InvoiceItem();
        credit.setAccountId(accountJson.getAccountId());
        credit.setInvoiceId(invoice.getInvoiceId());
        credit.setAmount(creditAmount);
        final InvoiceItems credits = new InvoiceItems();
        credits.add(credit);

        final InvoiceItems objFromJson = creditApi.createCredits(credits, true, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertTrue(objFromJson.get(0).getAmount().compareTo(creditAmount) == 0);
    }

    @Test(groups = "slow", description = "Cannot add a credit if the account doesn't exist")
    public void testAccountDoesNotExist() throws Exception {
        final InvoiceItem credit = new InvoiceItem();
        credit.setAccountId(UUID.randomUUID());
        credit.setAmount(BigDecimal.TEN);

        final InvoiceItems credits = new InvoiceItems();
        credits.add(credit);

        // Try to create the credit
        final InvoiceItems result = creditApi.createCredits(credits, true, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(result.size(), 0);
    }

    @Test(groups = "slow", description = "Cannot credit a badly formatted credit")
    public void testBadRequest() throws Exception {
        final InvoiceItem credit = new InvoiceItem();
        credit.setAccountId(accountJson.getAccountId());
        credit.setAmount(BigDecimal.TEN.negate());
        final InvoiceItems credits = new InvoiceItems();
        credits.add(credit);

        // Try to create the credit
        try {
            creditApi.createCredits(credits, true, NULL_PLUGIN_PROPERTIES, requestOptions);
            fail();
        } catch (final KillBillClientException e) {
        }
    }

    @Test(groups = "slow", description = "Cannot retrieve a non existing credit")
    public void testCreditDoesNotExist() throws Exception {
        assertNull(creditApi.getCredit(UUID.randomUUID(), requestOptions));
    }
}
