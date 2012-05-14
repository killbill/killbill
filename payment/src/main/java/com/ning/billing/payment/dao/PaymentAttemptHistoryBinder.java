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

import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@BindingAnnotation(PaymentAttemptHistoryBinder.PaymentAttemptHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PaymentAttemptHistoryBinder {
    public static class PaymentAttemptHistoryBinderFactory extends BinderBase implements BinderFactory {
        @Override
        public Binder<PaymentAttemptHistoryBinder, EntityHistory<PaymentAttempt>> build(Annotation annotation) {
            return new Binder<PaymentAttemptHistoryBinder, EntityHistory<PaymentAttempt>>() {
                @Override
                public void bind(SQLStatement q, PaymentAttemptHistoryBinder bind, EntityHistory<PaymentAttempt> history) {
                    q.bind("recordId", history.getValue());
                    q.bind("changeType", history.getChangeType().toString());

                    PaymentAttempt paymentAttempt = history.getEntity();
                    q.bind("id", paymentAttempt.getId().toString());
                    q.bind("invoice_id", paymentAttempt.getInvoiceId().toString());
                    q.bind("account_id", paymentAttempt.getAccountId().toString());
                    q.bind("amount", paymentAttempt.getAmount());
                    q.bind("currency", paymentAttempt.getCurrency().toString());
                    q.bind("invoice_date", getDate(paymentAttempt.getInvoiceDate()));
                    q.bind("payment_attempt_date", getDate(paymentAttempt.getPaymentAttemptDate()));
                    q.bind("payment_id", paymentAttempt.getPaymentId().toString());
                    q.bind("retry_count", paymentAttempt.getRetryCount());
                }
            };
        }
    }
}