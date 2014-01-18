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

package com.ning.billing.jaxrs;

import java.util.List;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.Invoice;
import com.ning.billing.client.model.Subscription;

public class TestInvoiceNotification extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can trigger an invoice notification")
    public void testTriggerNotification() throws Exception {
        final Account accountJson = createScenarioWithOneInvoice();

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        Assert.assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        killBillClient.triggerInvoiceNotification(invoice.getInvoiceId(), createdBy, reason, comment);
    }

    private Account createScenarioWithOneInvoice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();
        Assert.assertNotNull(accountJson);

        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), "76213", "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        Assert.assertNotNull(subscriptionJson);

        return accountJson;
    }
}
