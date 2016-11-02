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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Credit;
import org.killbill.billing.client.model.Invoice;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestCredit extends TestJaxrsBase {

    Account accountJson;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
    }

    @Test(groups = "slow", description = "Can add a credit to an existing invoice")
    public void testAddCreditToInvoice() throws Exception {
        //final Invoice invoice = killBillClient.getInvoicesForAccount(accountJson.getAccountId()).get(1);

        final DateTime effectiveDate = clock.getUTCNow();
        final BigDecimal creditAmount = BigDecimal.ONE;
        final Credit credit = new Credit();
        credit.setAccountId(accountJson.getAccountId());
        credit.setCreditAmount(creditAmount);
        credit.setDescription("description");
        Credit objFromJson = killBillClient.createCredit(credit, false, createdBy, reason, comment);

        final UUID invoiceId = objFromJson.getInvoiceId();
        credit.setInvoiceId(invoiceId);
        objFromJson = killBillClient.createCredit(credit, false, createdBy, reason, comment);

        // We can't just compare the object via .equals() due e.g. to the invoice id
        assertEquals(objFromJson.getAccountId(), accountJson.getAccountId());
        assertEquals(objFromJson.getInvoiceId(), invoiceId);
        assertEquals(objFromJson.getCreditAmount().compareTo(creditAmount), 0);
        assertEquals(objFromJson.getEffectiveDate().compareTo(effectiveDate.toLocalDate()), 0);
        assertEquals(objFromJson.getDescription().compareTo("description"), 0);
    }

    @Test(groups = "slow", description = "Can add a credit to an existing account",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = ".*it is already in COMMITTED status")
    public void testAddCreditToCommittedInvoice() throws Exception {
        final Invoice invoice = killBillClient.getInvoicesForAccount(accountJson.getAccountId()).get(1);

        final DateTime effectiveDate = clock.getUTCNow();
        final BigDecimal creditAmount = BigDecimal.ONE;
        final Credit credit = new Credit();
        credit.setAccountId(accountJson.getAccountId());
        credit.setInvoiceId(invoice.getInvoiceId());
        credit.setCreditAmount(creditAmount);
        final Credit objFromJson = killBillClient.createCredit(credit, true, createdBy, reason, comment);
    }

    @Test(groups = "slow", description = "Cannot add a credit if the account doesn't exist")
    public void testAccountDoesNotExist() throws Exception {
        final Credit credit = new Credit();
        credit.setAccountId(UUID.randomUUID());
        credit.setCreditAmount(BigDecimal.TEN);

        // Try to create the credit
        assertNull(killBillClient.createCredit(credit, true, createdBy, reason, comment));
    }

    @Test(groups = "slow", description = "Cannot credit a badly formatted credit")
    public void testBadRequest() throws Exception {
        final Credit credit = new Credit();
        credit.setAccountId(accountJson.getAccountId());
        credit.setCreditAmount(BigDecimal.TEN.negate());

        // Try to create the credit
        try {
            killBillClient.createCredit(credit, true, createdBy, reason, comment);
            fail();
        } catch (final KillBillClientException e) {
        }
    }

    @Test(groups = "slow", description = "Cannot retrieve a non existing credit")
    public void testCreditDoesNotExist() throws Exception {
        assertNull(killBillClient.getCredit(UUID.randomUUID()));
    }
}
