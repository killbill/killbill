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

import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.analytics.utils.Rounder;

@BindingAnnotation(BusinessInvoiceItemBinder.BiiBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface BusinessInvoiceItemBinder {
    public static class BiiBinderFactory implements BinderFactory {
        public Binder build(final Annotation annotation) {
            return new Binder<BusinessInvoiceItemBinder, BusinessInvoiceItem>() {
                public void bind(final SQLStatement q, final BusinessInvoiceItemBinder bind, final BusinessInvoiceItem invoiceItem) {
                    q.bind("item_id", invoiceItem.getItemId().toString());

                    final DateTime dateTimeNow = new DateTime(DateTimeZone.UTC);
                    if (invoiceItem.getCreatedDate() != null) {
                        q.bind("created_date", invoiceItem.getCreatedDate().getMillis());
                    } else {
                        q.bind("created_date", dateTimeNow.getMillis());
                    }

                    if (invoiceItem.getUpdatedDate() != null) {
                        q.bind("updated_date", invoiceItem.getUpdatedDate().getMillis());
                    } else {
                        q.bind("updated_date", dateTimeNow.getMillis());
                    }

                    q.bind("invoice_id", invoiceItem.getInvoiceId().toString());
                    q.bind("item_type", invoiceItem.getItemType());
                    q.bind("external_key", invoiceItem.getExternalKey());
                    q.bind("product_name", invoiceItem.getProductName());
                    q.bind("product_type", invoiceItem.getProductType());
                    q.bind("product_category", invoiceItem.getProductCategory());
                    q.bind("slug", invoiceItem.getSlug());
                    q.bind("phase", invoiceItem.getPhase());
                    q.bind("billing_period", invoiceItem.getBillingPeriod());

                    if (invoiceItem.getStartDate() != null) {
                        q.bind("start_date", invoiceItem.getStartDate());
                    } else {
                        q.bindNull("start_date", Types.BIGINT);
                    }

                    if (invoiceItem.getEndDate() != null) {
                        q.bind("end_date", invoiceItem.getEndDate());
                    } else {
                        q.bindNull("end_date", Types.BIGINT);
                    }

                    q.bind("amount", Rounder.round(invoiceItem.getAmount()));
                    q.bind("currency", invoiceItem.getCurrency().toString());
                }
            };
        }
    }
}
