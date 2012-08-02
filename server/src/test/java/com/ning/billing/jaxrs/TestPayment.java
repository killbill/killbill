/*
 * Copyright 2010-2012 Ning, Inc.
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
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;

public class TestPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testPaymentWithRefund() throws Exception {
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<PaymentJsonSimple> firstPaymentForAccount = getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(firstPaymentForAccount.size(), 1);

        final String paymentId = firstPaymentForAccount.get(0).getPaymentId();
        final BigDecimal paymentAmount = firstPaymentForAccount.get(0).getAmount();

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = firstPaymentForAccount.get(0).getPaymentMethodId();
        final PaymentMethodJson paymentMethodJson = getPaymentMethodWithPluginInfo(paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        Assert.assertNotNull(paymentMethodJson.getPluginInfo().getExternalPaymentId());

        // Verify the refunds
        final List<RefundJson> objRefundFromJson = getRefundsForPayment(paymentId);
        Assert.assertEquals(objRefundFromJson.size(), 0);

        // Issue a refund for the full amount
        final RefundJson refundJsonCheck = createRefund(paymentId, paymentAmount);
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        // Verify the refunds
        final List<RefundJson> retrievedRefunds = getRefundsForPayment(paymentId);
        Assert.assertEquals(retrievedRefunds.size(), 1);
    }
}
