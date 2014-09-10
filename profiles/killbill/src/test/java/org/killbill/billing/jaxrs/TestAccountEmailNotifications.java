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

import java.util.UUID;

import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.InvoiceEmail;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAccountEmailNotifications extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can toggle email notifications")
    public void testSetAndUnsetEmailNotifications() throws Exception {
        final Account input = createAccount();
        final UUID accountId = input.getAccountId();

        final InvoiceEmail invoiceEmailJsonWithNotifications = new InvoiceEmail(accountId, true);
        final InvoiceEmail invoiceEmailJsonWithoutNotifications = new InvoiceEmail(accountId, false);

        // Verify the initial state
        final InvoiceEmail firstInvoiceEmailJson = killBillClient.getEmailNotificationsForAccount(accountId);
        Assert.assertEquals(firstInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertFalse(firstInvoiceEmailJson.isNotifiedForInvoices());

        // Enable email notifications
        killBillClient.updateEmailNotificationsForAccount(invoiceEmailJsonWithNotifications, createdBy, reason, comment);

        // Verify we can retrieve it
        final InvoiceEmail secondInvoiceEmailJson = killBillClient.getEmailNotificationsForAccount(accountId);
        Assert.assertEquals(secondInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertTrue(secondInvoiceEmailJson.isNotifiedForInvoices());

        // Disable email notifications
        killBillClient.updateEmailNotificationsForAccount(invoiceEmailJsonWithoutNotifications, createdBy, reason, comment);

        // Verify we can retrieve it
        final InvoiceEmail thirdInvoiceEmailJson = killBillClient.getEmailNotificationsForAccount(accountId);
        Assert.assertEquals(thirdInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertFalse(thirdInvoiceEmailJson.isNotifiedForInvoices());
    }
}
