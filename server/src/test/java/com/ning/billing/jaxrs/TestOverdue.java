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

import java.math.BigDecimal;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonWithItems;

import static org.testng.Assert.assertEquals;

public class TestOverdue extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testOverdueStatus() throws Exception {
        // Create an account without a payment method
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJsonWithItems> invoices = getInvoicesWithItemsForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String bundleId = invoices.get(1).getItems().get(0).getBundleId();

        // We're still clear - see the configuration
        Assert.assertTrue(getOverdueStateForBundle(bundleId).isClearState());

        clock.addDays(30);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(getOverdueStateForBundle(bundleId).getName(), "OD1");

        clock.addDays(10);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(getOverdueStateForBundle(bundleId).getName(), "OD2");

        clock.addDays(10);
        crappyWaitForLackOfProperSynchonization();
        Assert.assertEquals(getOverdueStateForBundle(bundleId).getName(), "OD3");

        // Post external payments
        for (final InvoiceJsonSimple invoice : getInvoicesForAccount(accountJson.getAccountId())) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                createExternalPayment(accountJson, invoice.getInvoiceId(), invoice.getBalance());
            }
        }

        // Wait a bit for overdue to pick up the payment events...
        crappyWaitForLackOfProperSynchonization();

        // Verify we're in clear state
        Assert.assertTrue(getOverdueStateForBundle(bundleId).isClearState());
    }
}
