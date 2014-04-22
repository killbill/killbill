/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.DirectPayment;
import org.killbill.billing.client.model.DirectTransaction;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDirectPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testRetrievePayment() throws Exception {

        final Account account = createAccountWithDefaultPaymentMethod();

        final DirectTransaction transaction = new DirectTransaction();
        transaction.setAmount(BigDecimal.TEN);
        transaction.setCurrency(account.getCurrency());
        transaction.setExternalKey("foo");
        transaction.setTransactionType("AUTHORIZE");

        final DirectPayment retrievedPaymentJson = killBillClient.createDirectPayment(account.getAccountId(), transaction, createdBy, reason, comment);

        assertEquals(retrievedPaymentJson.getAccountId(), account.getAccountId());
        Assert.assertNotNull(retrievedPaymentJson.getDirectPaymentId());
        Assert.assertNotNull(retrievedPaymentJson.getPaymentNumber());
        assertEquals(retrievedPaymentJson.getAuthAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(retrievedPaymentJson.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(retrievedPaymentJson.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(retrievedPaymentJson.getCurrency(), account.getCurrency());
        assertEquals(retrievedPaymentJson.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(retrievedPaymentJson.getTransactions().size(), 1);

        assertEquals(retrievedPaymentJson.getTransactions().get(0).getDirectPaymentId(), retrievedPaymentJson.getDirectPaymentId());
        Assert.assertNotNull(retrievedPaymentJson.getTransactions().get(0).getDirectTransactionId());
        assertEquals(retrievedPaymentJson.getTransactions().get(0).getTransactionType(), "AUTHORIZE");
        assertEquals(retrievedPaymentJson.getTransactions().get(0).getStatus(), "SUCCESS");
        assertEquals(retrievedPaymentJson.getTransactions().get(0).getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(retrievedPaymentJson.getTransactions().get(0).getExternalKey(), "foo");
    }
}
