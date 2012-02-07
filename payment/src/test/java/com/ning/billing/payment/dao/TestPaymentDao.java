/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import com.ning.billing.payment.api.PaymentInfo;

public abstract class TestPaymentDao {

    protected PaymentDao dao;

    @Test
    public void testCreatePayment() {
        PaymentInfo paymentInfo = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                                                           .setAmount(BigDecimal.TEN)
                                                           .setStatus("Processed")
                                                           .setBankIdentificationNumber("1234")
                                                           .setPaymentNumber("12345")
                                                           .setPaymentMethodId("12345")
                                                           .setReferenceId("12345")
                                                           .setType("Electronic")
                                                           .setCreatedDate(new DateTime(DateTimeZone.UTC))
                                                           .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                                                           .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                                                           .build();

        dao.savePaymentInfo(paymentInfo);
    }

    @Test
    public void testUpdatePayment() {
        PaymentInfo paymentInfo = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                                                           .setAmount(BigDecimal.TEN)
                                                           .setStatus("Processed")
                                                           .setBankIdentificationNumber("1234")
                                                           .setPaymentNumber("12345")
                                                           .setPaymentMethodId("12345")
                                                           .setReferenceId("12345")
                                                           .setType("Electronic")
                                                           .setCreatedDate(new DateTime(DateTimeZone.UTC))
                                                           .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                                                           .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                                                           .build();

        dao.savePaymentInfo(paymentInfo);

        dao.updatePaymentInfo("CreditCard", paymentInfo.getPaymentId(), "Visa", "US");

    }

}
