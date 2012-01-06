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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.util.entity.EntityCollectionDao;
import com.ning.billing.util.entity.EntityDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(InvoiceItemSqlDao.InvoiceItemMapper.class)
public interface InvoiceItemSqlDao extends EntityDao<InvoiceItem> {
    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByAccount(@Bind("accountId") final String accountId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId);

    @Override
    @SqlUpdate
    void create(@InvoiceItemBinder final InvoiceItem invoiceItem);

    @Override
    @SqlUpdate
    void update(@InvoiceItemBinder final InvoiceItem invoiceItem);

    @SqlBatch
    void create(@InvoiceItemBinder final List<InvoiceItem> items);

    @BindingAnnotation(InvoiceItemBinder.InvoiceItemBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceItemBinder {
        public static class InvoiceItemBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<InvoiceItemBinder, InvoiceItem>() {
                    public void bind(SQLStatement q, InvoiceItemBinder bind, InvoiceItem item) {
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

    public static class InvoiceItemMapper implements ResultSetMapper<InvoiceItem> {
        @Override
        public InvoiceItem map(int index, ResultSet result, StatementContext context) throws SQLException {
            UUID id = UUID.fromString(result.getString("id"));
            UUID invoiceId = UUID.fromString(result.getString("invoice_id"));
            UUID subscriptionId = UUID.fromString(result.getString("subscription_id"));
            DateTime startDate = new DateTime(result.getTimestamp("start_date"));
            DateTime endDate = new DateTime(result.getTimestamp("end_date"));
            String description = result.getString("description");
            BigDecimal amount = result.getBigDecimal("amount");
            BigDecimal rate = result.getBigDecimal("rate");
            Currency currency = Currency.valueOf(result.getString("currency"));

            return new DefaultInvoiceItem(id, invoiceId, subscriptionId, startDate, endDate, description, amount, rate , currency);
        }
    }
}
