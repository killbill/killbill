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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.entity.dao.EntitySqlDao;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(CreditInvoiceItemSqlDao.CreditInvoiceItemMapper.class)
public interface CreditInvoiceItemSqlDao extends EntitySqlDao<InvoiceItem> {
    @SqlQuery
    List<Long> getRecordIds(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByAccount(@Bind("accountId") final String accountId);

    @Override
    @SqlUpdate
    void create(@CreditInvoiceItemBinder final InvoiceItem invoiceItem, @CallContextBinder final CallContext context);

    @SqlBatch
    void create(@CreditInvoiceItemBinder final List<InvoiceItem> items, @CallContextBinder final CallContext context);

    @SqlBatch(transactional = false)
    void batchCreateFromTransaction(@CreditInvoiceItemBinder final List<InvoiceItem> items, @CallContextBinder final CallContext context);

    @BindingAnnotation(CreditInvoiceItemBinder.CreditInvoiceItemBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface CreditInvoiceItemBinder {
        public static class CreditInvoiceItemBinderFactory implements BinderFactory {
            public Binder build(final Annotation annotation) {
                return new Binder<CreditInvoiceItemBinder, CreditBalanceAdjInvoiceItem>() {
                    public void bind(final SQLStatement q, final CreditInvoiceItemBinder bind, final CreditBalanceAdjInvoiceItem item) {
                        q.bind("id", item.getId().toString());
                        q.bind("invoiceId", item.getInvoiceId().toString());
                        q.bind("accountId", item.getAccountId().toString());
                        q.bind("creditDate", item.getStartDate().toDate());
                        q.bind("amount", item.getAmount());
                        q.bind("currency", item.getCurrency().toString());
                    }
                };
            }
        }
    }

    public class CreditInvoiceItemMapper implements ResultSetMapper<InvoiceItem> {
        @Override
        public InvoiceItem map(final int index, final ResultSet result, final StatementContext ctx) throws SQLException {
            final UUID id = UUID.fromString(result.getString("id"));
            final UUID invoiceId = UUID.fromString(result.getString("invoice_id"));
            final UUID accountId = UUID.fromString(result.getString("account_id"));
            final DateTime creditDate = new DateTime(result.getTimestamp("credit_date"));
            final BigDecimal amount = result.getBigDecimal("amount");
            final Currency currency = Currency.valueOf(result.getString("currency"));
            return new CreditBalanceAdjInvoiceItem(id, invoiceId, accountId, creditDate, amount, currency);
        }
    }
}
