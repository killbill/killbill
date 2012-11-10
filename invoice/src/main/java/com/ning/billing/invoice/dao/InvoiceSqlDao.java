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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.dao.InvoiceSqlDao.InvoiceModelDaoMapper;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper({InvoiceModelDaoMapper.class, UuidMapper.class})
public interface InvoiceSqlDao extends EntitySqlDao<InvoiceModelDao> {

    @SqlQuery
    List<InvoiceModelDao> getInvoicesByAccount(@Bind("accountId") final String accountId,
                                               @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceModelDao> getAllInvoicesByAccount(@Bind("accountId") final String string,
                                                  @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceModelDao> getInvoicesByAccountAfterDate(@Bind("accountId") final String accountId,
                                                        @Bind("fromDate") final Date fromDate,
                                                        @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceModelDao> getInvoicesBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                    @BindBean final InternalTenantContext context);

    @SqlQuery
    UUID getInvoiceIdByPaymentId(@Bind("paymentId") final String paymentId,
                                 @BindBean final InternalTenantContext context);

    public static class InvoiceModelDaoMapper extends MapperBase implements ResultSetMapper<InvoiceModelDao> {

        @Override
        public InvoiceModelDao map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = UUID.fromString(result.getString("id"));
            final UUID accountId = UUID.fromString(result.getString("account_id"));
            final int invoiceNumber = result.getInt("invoice_number");
            final LocalDate invoiceDate = getDate(result, "invoice_date");
            final LocalDate targetDate = getDate(result, "target_date");
            final Currency currency = Currency.valueOf(result.getString("currency"));
            final boolean isMigrationInvoice = result.getBoolean("migrated");
            final DateTime createdDate = getDateTime(result, "created_date");

            return new InvoiceModelDao(id, createdDate, accountId, invoiceNumber, invoiceDate, targetDate, currency, isMigrationInvoice);
        }
    }
}

