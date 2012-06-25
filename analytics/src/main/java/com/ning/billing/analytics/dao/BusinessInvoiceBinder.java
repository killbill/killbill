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

import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.utils.Rounder;

@BindingAnnotation(BusinessInvoiceBinder.BinBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessInvoiceBinder {
    public static class BinBinderFactory implements BinderFactory {
        public Binder build(final Annotation annotation) {
            return new Binder<BusinessInvoiceBinder, BusinessInvoice>() {
                public void bind(final SQLStatement q, final BusinessInvoiceBinder bind, final BusinessInvoice invoice) {
                    q.bind("invoice_id", invoice.getInvoiceId().toString());

                    final DateTime dateTimeNow = new DateTime(DateTimeZone.UTC);
                    if (invoice.getCreatedDate() != null) {
                        q.bind("created_date", invoice.getCreatedDate().getMillis());
                    } else {
                        q.bind("created_date", dateTimeNow.getMillis());
                    }

                    if (invoice.getUpdatedDate() != null) {
                        q.bind("updated_date", invoice.getUpdatedDate().getMillis());
                    } else {
                        q.bind("updated_date", dateTimeNow.getMillis());
                    }

                    q.bind("account_key", invoice.getAccountKey());

                    if (invoice.getInvoiceDate() != null) {
                        q.bind("invoice_date", invoice.getInvoiceDate().getMillis());
                    } else {
                        q.bindNull("invoice_date", Types.BIGINT);
                    }

                    if (invoice.getTargetDate() != null) {
                        q.bind("target_date", invoice.getTargetDate().getMillis());
                    } else {
                        q.bindNull("target_date", Types.BIGINT);
                    }

                    q.bind("currency", invoice.getCurrency().toString());
                    q.bind("balance", Rounder.round(invoice.getBalance()));
                    q.bind("amount_paid", Rounder.round(invoice.getAmountPaid()));
                    q.bind("amount_charged", Rounder.round(invoice.getAmountCharged()));
                    q.bind("amount_credited", Rounder.round(invoice.getAmountCredited()));
                }
            };
        }
    }
}
