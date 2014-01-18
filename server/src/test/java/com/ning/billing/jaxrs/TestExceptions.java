/*
 * Copyright 2010-2013 Ning, Incc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ning.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.client.KillBillClientException;
import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.Chargeback;
import com.ning.billing.client.model.Payment;
import com.ning.billing.invoice.api.InvoiceApiException;

import static org.testng.Assert.fail;

public class TestExceptions extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testExceptionMapping() throws Exception {
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final List<Payment> payments = killBillClient.getPaymentsForAccount(account.getAccountId());
        final Chargeback input = new Chargeback();
        input.setAmount(BigDecimal.TEN.negate());
        input.setPaymentId(payments.get(0).getPaymentId());

        try {
            killBillClient.createChargeBack(input, createdBy, reason, comment);
            fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getClassName(), InvoiceApiException.class.getName());
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE.getCode());
            Assert.assertFalse(e.getBillingException().getStackTrace().isEmpty());
        }
    }
}
