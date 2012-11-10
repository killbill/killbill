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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;

@BindingAnnotation(PaymentAttemptHistoryBinder.PaymentAttemptHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PaymentAttemptHistoryBinder {


    public static class PaymentAttemptHistoryBinderFactory extends BinderBase implements BinderFactory {
        @Override
        public Binder<PaymentAttemptHistoryBinder, EntityHistory<PaymentAttemptModelDao>> build(final Annotation annotation) {
            return new Binder<PaymentAttemptHistoryBinder, EntityHistory<PaymentAttemptModelDao>>() {
                @Override
                public void bind(final SQLStatement<?> q, final PaymentAttemptHistoryBinder bind, final EntityHistory<PaymentAttemptModelDao> history) {
                  //  q.bind("recordId", history.getValue());
                    q.bind("changeType", history.getChangeType().toString());
                    final PaymentAttemptModelDao attempt = history.getEntity();
                    q.bind("id", attempt.getId().toString());
                    q.bind("paymentId", attempt.getPaymentId().toString());
                    q.bind("processingStatus", attempt.getProcessingStatus().toString());
                    q.bind("gatewayErrorCode", attempt.getGatewayErrorCode() );
                    q.bind("gatewayErrorMsg", attempt.getGatewayErrorMsg() );
                    q.bind("requestedAmount", attempt.getRequestedAmount());
                }
            };
        }
    }
}
