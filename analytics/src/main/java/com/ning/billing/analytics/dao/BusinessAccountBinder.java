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

package com.ning.billing.analytics.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Types;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.analytics.model.BusinessAccountModelDao;

@BindingAnnotation(BusinessAccountBinder.BacBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessAccountBinder {
    public static class BacBinderFactory implements BinderFactory {
        public Binder build(final Annotation annotation) {
            return new Binder<BusinessAccountBinder, BusinessAccountModelDao>() {
                public void bind(final SQLStatement q, final BusinessAccountBinder bind, final BusinessAccountModelDao account) {
                    final DateTime dateTimeNow = new DateTime(DateTimeZone.UTC);

                    if (account.getCreatedDate() != null) {
                        q.bind("created_date", account.getCreatedDate().getMillis());
                    } else {
                        q.bind("created_date", dateTimeNow.getMillis());
                    }

                    if (account.getUpdatedDate() != null) {
                        q.bind("updated_date", account.getUpdatedDate().getMillis());
                    } else {
                        q.bind("updated_date", dateTimeNow.getMillis());
                    }

                    q.bind("account_id", account.getAccountId().toString());
                    q.bind("account_key", account.getKey());
                    q.bind("balance", account.getRoundedBalance());
                    q.bind("name", account.getName());
                    if (account.getLastInvoiceDate() != null) {
                        q.bind("last_invoice_date", account.getLastInvoiceDate().toDate());
                    } else {
                        q.bindNull("last_invoice_date", Types.DATE);
                    }
                    q.bind("total_invoice_balance", account.getRoundedTotalInvoiceBalance());
                    q.bind("last_payment_status", account.getLastPaymentStatus());
                    q.bind("payment_method", account.getPaymentMethod());
                    q.bind("credit_card_type", account.getCreditCardType());
                    q.bind("billing_address_country", account.getBillingAddressCountry());
                    q.bind("currency", account.getCurrency());
                }
            };
        }
    }
}
