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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.analytics.utils.Rounder;

@BindingAnnotation(BusinessInvoicePaymentBinder.BipBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessInvoicePaymentBinder {
    public static class BipBinderFactory implements BinderFactory {
        @Override
        public Binder build(final Annotation annotation) {
            return new Binder<BusinessInvoicePaymentBinder, BusinessInvoicePaymentModelDao>() {
                @Override
                public void bind(final SQLStatement q, final BusinessInvoicePaymentBinder bind, final BusinessInvoicePaymentModelDao invoicePayment) {
                    q.bind("payment_id", invoicePayment.getPaymentId().toString());

                    final DateTime dateTimeNow = new DateTime(DateTimeZone.UTC);
                    if (invoicePayment.getCreatedDate() != null) {
                        q.bind("created_date", invoicePayment.getCreatedDate().getMillis());
                    } else {
                        q.bind("created_date", dateTimeNow.getMillis());
                    }

                    if (invoicePayment.getUpdatedDate() != null) {
                        q.bind("updated_date", invoicePayment.getUpdatedDate().getMillis());
                    } else {
                        q.bind("updated_date", dateTimeNow.getMillis());
                    }

                    q.bind("ext_first_payment_ref_id", invoicePayment.getExtFirstPaymentRefId());
                    q.bind("ext_second_payment_ref_id", invoicePayment.getExtSecondPaymentRefId());
                    q.bind("account_key", invoicePayment.getAccountKey());
                    q.bind("invoice_id", invoicePayment.getInvoiceId().toString());

                    if (invoicePayment.getEffectiveDate() != null) {
                        q.bind("effective_date", invoicePayment.getEffectiveDate().getMillis());
                    } else {
                        q.bindNull("effective_date", Types.BIGINT);
                    }

                    q.bind("amount", Rounder.round(invoicePayment.getAmount()));
                    q.bind("currency", invoicePayment.getCurrency().toString());
                    q.bind("payment_error", invoicePayment.getPaymentError());
                    q.bind("processing_status", invoicePayment.getProcessingStatus());
                    q.bind("requested_amount", Rounder.round(invoicePayment.getRequestedAmount()));
                    q.bind("plugin_name", invoicePayment.getPluginName());
                    q.bind("payment_type", invoicePayment.getPaymentType());
                    q.bind("payment_method", invoicePayment.getPaymentMethod());
                    q.bind("card_type", invoicePayment.getCardType());
                    q.bind("card_country", invoicePayment.getCardCountry());
                    q.bind("invoice_payment_type", invoicePayment.getInvoicePaymentType());

                    final UUID linkedInvoicePaymentId = invoicePayment.getLinkedInvoicePaymentId();
                    if (linkedInvoicePaymentId != null) {
                        q.bind("linked_invoice_payment_id", linkedInvoicePaymentId.toString());
                    } else {
                        q.bindNull("linked_invoice_payment_id", Types.VARCHAR);
                    }
                }
            };
        }
    }
}
