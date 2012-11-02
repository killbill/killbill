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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.MapperBase;

public class BusinessInvoiceMapper extends MapperBase implements ResultSetMapper<BusinessInvoiceModelDao> {

    @Override
    public BusinessInvoiceModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID invoiceId = UUID.fromString(r.getString(1));
        final Integer invoiceNumber = r.getInt(2);
        final DateTime createdDate = new DateTime(r.getLong(3), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong(4), DateTimeZone.UTC);
        final UUID accountId = UUID.fromString(r.getString(5));
        final String accountKey = r.getString(6);
        final LocalDate invoiceDate = getDate(r, "invoice_date");
        final LocalDate targetDate = getDate(r, "target_date");
        final Currency currency = Currency.valueOf(r.getString(9));
        final BigDecimal balance = BigDecimal.valueOf(r.getDouble(10));
        final BigDecimal amountPaid = BigDecimal.valueOf(r.getDouble(11));
        final BigDecimal amountCharged = BigDecimal.valueOf(r.getDouble(12));
        final BigDecimal amountCredited = BigDecimal.valueOf(r.getDouble(13));

        return new BusinessInvoiceModelDao(accountId, accountKey, amountCharged, amountCredited, amountPaid, balance, createdDate, currency,
                                           invoiceDate, invoiceId, invoiceNumber, targetDate, updatedDate);
    }
}
