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

package org.killbill.billing.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;

public class TestDefaultNoOpPaymentInfoPlugin extends PaymentTestSuiteNoDB {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final UUID kbPaymentId = UUID.randomUUID();
        final UUID kbTransactionId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("1.394810E-3");
        final DateTime effectiveDate = clock.getUTCNow().plusDays(1);
        final DateTime createdDate = clock.getUTCNow();
        final PaymentPluginStatus status = PaymentPluginStatus.UNDEFINED;
        final String error = UUID.randomUUID().toString();

        final DefaultNoOpPaymentInfoPlugin info = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, Currency.USD, effectiveDate, createdDate,
                                                                                   status, error, null);
        Assert.assertEquals(info, info);

        final DefaultNoOpPaymentInfoPlugin sameInfo = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, Currency.USD, effectiveDate, createdDate,
                                                                                       status, error, null);
        Assert.assertEquals(sameInfo, info);

        final DefaultNoOpPaymentInfoPlugin otherInfo = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, Currency.USD, effectiveDate, createdDate,
                                                                                        status, UUID.randomUUID().toString(), null);
        Assert.assertNotEquals(otherInfo, info);
    }
}
