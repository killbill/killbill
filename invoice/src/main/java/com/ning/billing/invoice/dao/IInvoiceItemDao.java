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

package com.ning.billing.invoice.dao;

import com.ning.billing.invoice.api.IInvoiceItem;
import com.ning.billing.invoice.model.InvoiceItemList;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.lang.annotation.*;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(InvoiceItemMapper.class)
public interface IInvoiceItemDao {
    @SqlQuery
    InvoiceItemList getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    InvoiceItemList getInvoiceItemsByAccount(@Bind("accountId") final String accountId);

    @SqlUpdate
    void createInvoiceItem(@InvoiceItemBinder final IInvoiceItem invoiceItem);

    @BindingAnnotation(InvoiceItemBinder.InvoiceItemBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceItemBinder {
        public static class InvoiceItemBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<InvoiceItemBinder, IInvoiceItem>() {
                    public void bind(SQLStatement q, InvoiceItemBinder bind, IInvoiceItem item) {
                        q.bind("id", item.getId().toString());
                        q.bind("invoiceId", item.getInvoiceId().toString());
                        q.bind("subscriptionId", item.getSubscriptionId().toString());
                        q.bind("startDate", item.getStartDate().toDate());
                        q.bind("endDate", item.getEndDate().toDate());
                        q.bind("description", item.getDescription());
                        q.bind("amount", item.getAmount());
                        q.bind("rate", item.getRate());
                        q.bind("currency", item.getCurrency().toString());
                    }
                };
            }
        }
    }
}
