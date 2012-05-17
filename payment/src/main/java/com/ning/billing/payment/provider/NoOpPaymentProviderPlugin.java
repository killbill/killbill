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

package com.ning.billing.payment.provider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethodInfo;
import com.ning.billing.payment.api.PaymentProviderAccount;

public class NoOpPaymentProviderPlugin implements PaymentProviderPlugin {

    private boolean makeAllInvoicesFail;

    public boolean isMakeAllInvoicesFail() {
        return makeAllInvoicesFail;
    }

    public void setMakeAllInvoicesFail(boolean makeAllInvoicesFail) {
        this.makeAllInvoicesFail = makeAllInvoicesFail;
    } 

    @Override
    public Either<PaymentErrorEvent, PaymentInfoEvent> processInvoice(Account account, Invoice invoice) {
        if (makeAllInvoicesFail) {
            return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unknown", "test error", account.getId(), invoice.getId(), null));
        }
        PaymentInfoEvent payment = new DefaultPaymentInfoEvent.Builder()
                                             .setPaymentId(UUID.randomUUID().toString())
                                             .setAmount(invoice.getBalance())
                                             .setStatus("Processed")
                                             .setCreatedDate(new DateTime(DateTimeZone.UTC))
                                             .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                                             .setType("Electronic")
                                             .build();
        return Either.right(payment);
    }

    @Override
    public Either<PaymentErrorEvent, PaymentInfoEvent> getPaymentInfo(String paymentId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, String> createPaymentProviderAccount(Account account) {
        return Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("unsupported",
                                            "Account creation not supported in this plugin",
                                            account.getId(),
                                            null, null));
    }

    @Override
    public Either<PaymentErrorEvent, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, String> addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        return Either.right(null);
    }

    public void setDefaultPaymentMethodOnAccount(PaymentProviderAccount account, String paymentMethodId) {
        // NO-OP
    }

    @Override
    public Either<PaymentErrorEvent, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethod) {
        return Either.right(paymentMethod);
    }

    @Override
    public Either<PaymentErrorEvent, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, PaymentMethodInfo> getPaymentMethodInfo(String paymentMethodId) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, List<PaymentMethodInfo>> getPaymentMethods(final String accountKey) {
        return Either.right(Arrays.<PaymentMethodInfo>asList());
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentGateway(String accountKey) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentProviderAccountExistingContact(Account account) {
        return Either.right(null);
    }

    @Override
    public Either<PaymentErrorEvent, Void> updatePaymentProviderAccountWithNewContact(Account account) {
        return Either.right(null);
    }

    @Override
    public List<Either<PaymentErrorEvent, PaymentInfoEvent>> processRefund(Account account) {
        // TODO Auto-generated method stub
        return null;
    }

}
