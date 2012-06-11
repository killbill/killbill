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

@BindingAnnotation(PaymentMethodHistoryBinder.PaymentMethodHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface PaymentMethodHistoryBinder {
    public static class PaymentMethodHistoryBinderFactory extends BinderBase implements BinderFactory {
        @Override
        public Binder<PaymentMethodHistoryBinder, EntityHistory<PaymentMethodModelDao>> build(Annotation annotation) {
            return new Binder<PaymentMethodHistoryBinder, EntityHistory<PaymentMethodModelDao>>() {
                @Override
                public void bind(@SuppressWarnings("rawtypes") SQLStatement q, PaymentMethodHistoryBinder bind, EntityHistory<PaymentMethodModelDao> history) {
                    q.bind("recordId", history.getValue());
                    q.bind("changeType", history.getChangeType().toString());
                    PaymentMethodModelDao paymentMethod = history.getEntity();
                    q.bind("id", paymentMethod.getId().toString());
                    q.bind("isActive", paymentMethod.isActive());                    
                    q.bind("accountId", paymentMethod.getAccountId().toString());            
                    q.bind("pluginName", paymentMethod.getPluginName()); 
                    q.bind("externalId", paymentMethod.getExternalId());
                }
            };
        }
    }
}
