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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.EntitySqlDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(InvoiceSqlDao.InvoiceMapper.class)
public interface InvoiceSqlDao extends EntitySqlDao<Invoice>, AuditSqlDao, Transactional<InvoiceSqlDao>, Transmogrifier, CloseMe {

    @Override
    @SqlUpdate
    void create(@InvoiceBinder Invoice invoice,
                @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    List<Invoice> getInvoicesByAccount(@Bind("accountId") final String accountId,
                                       @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Invoice> getAllInvoicesByAccount(@Bind("accountId") final String string,
                                          @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Invoice> getInvoicesByAccountAfterDate(@Bind("accountId") final String accountId,
                                                @Bind("fromDate") final Date fromDate,
                                                @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Invoice> getInvoicesBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                            @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    @RegisterMapper(UuidMapper.class)
    UUID getInvoiceIdByPaymentId(@Bind("paymentId") final String paymentId,
                                 @InternalTenantContextBinder final InternalTenantContext context);

    @BindingAnnotation(InvoiceBinder.InvoiceBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceBinder {

        public static class InvoiceBinderFactory implements BinderFactory {

            @Override
            public Binder<InvoiceBinder, Invoice> build(final Annotation annotation) {
                return new Binder<InvoiceBinder, Invoice>() {
                    @Override
                    public void bind(@SuppressWarnings("rawtypes") final SQLStatement q, final InvoiceBinder bind, final Invoice invoice) {
                        q.bind("id", invoice.getId().toString());
                        q.bind("accountId", invoice.getAccountId().toString());
                        q.bind("invoiceDate", invoice.getInvoiceDate().toDate());
                        q.bind("targetDate", invoice.getTargetDate().toDate());
                        q.bind("currency", invoice.getCurrency().toString());
                        q.bind("migrated", invoice.isMigrationInvoice());
                    }
                };
            }
        }
    }

    public static class InvoiceMapper extends MapperBase implements ResultSetMapper<Invoice> {

        @Override
        public Invoice map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = UUID.fromString(result.getString("id"));
            final UUID accountId = UUID.fromString(result.getString("account_id"));
            final int invoiceNumber = result.getInt("invoice_number");
            final LocalDate invoiceDate = getDate(result, "invoice_date");
            final LocalDate targetDate = getDate(result, "target_date");
            final Currency currency = Currency.valueOf(result.getString("currency"));
            final boolean isMigrationInvoice = result.getBoolean("migrated");
            final DateTime createdDate = getDateTime(result, "created_date");

            return new DefaultInvoice(id, createdDate, accountId, invoiceNumber, invoiceDate, targetDate, currency, isMigrationInvoice);
        }
    }
}

