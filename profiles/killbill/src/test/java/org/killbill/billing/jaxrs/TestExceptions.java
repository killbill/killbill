/*
 * Copyright 2010-2013 Ning, Inc
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
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.InvoicePaymentTransaction;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class TestExceptions extends TestJaxrsBase {

    @Test(groups = "slow", enabled=false)
    public void testExceptionMapping() throws Exception {
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final List<InvoicePayment> payments = accountApi.getInvoicePayments(account.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        final InvoicePaymentTransaction input = new InvoicePaymentTransaction();
        input.setPaymentId(payments.get(0).getPaymentId());
        input.setAmount(BigDecimal.TEN.negate());
        try {
            invoicePaymentApi.createChargeback(payments.get(0).getPaymentId(), input, requestOptions);
            fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getClassName(), InvoiceApiException.class.getName());
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE.getCode());
            Assert.assertFalse(e.getBillingException().getStackTrace().isEmpty());
        }
    }
}
