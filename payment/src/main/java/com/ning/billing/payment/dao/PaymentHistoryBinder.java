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

import com.ning.billing.payment.api.PaymentInfoEvent;
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

@BindingAnnotation(PaymentHistoryBinder.PaymentHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PaymentHistoryBinder {
    public static class PaymentHistoryBinderFactory extends BinderBase implements BinderFactory {
        @Override
        public Binder<PaymentHistoryBinder, EntityHistory<PaymentInfoEvent>> build(Annotation annotation) {
            return new Binder<PaymentHistoryBinder, EntityHistory<PaymentInfoEvent>>() {
                @Override
                public void bind(SQLStatement q, PaymentHistoryBinder bind, EntityHistory<PaymentInfoEvent> history) {
                    q.bind("recordId", history.getValue());
                    q.bind("changeType", history.getChangeType().toString());

                    PaymentInfoEvent paymentInfo = history.getEntity();
                    q.bind("id", paymentInfo.getId().toString());
                    q.bind("amount", paymentInfo.getAmount());
                    q.bind("refund_amount", paymentInfo.getRefundAmount());
                    q.bind("payment_number", paymentInfo.getPaymentNumber());
                    q.bind("bank_identification_number", paymentInfo.getBankIdentificationNumber());
                    q.bind("status", paymentInfo.getStatus());
                    q.bind("payment_type", paymentInfo.getType());
                    q.bind("reference_id", paymentInfo.getReferenceId());
                    q.bind("payment_method_id", paymentInfo.getPaymentMethodId());
                    q.bind("payment_method", paymentInfo.getPaymentMethod());
                    q.bind("card_type", paymentInfo.getCardType());
                    q.bind("card_country", paymentInfo.getCardCountry());
                    q.bind("effective_date", getDate(paymentInfo.getEffectiveDate()));
                }
            };
        }
    }
}
