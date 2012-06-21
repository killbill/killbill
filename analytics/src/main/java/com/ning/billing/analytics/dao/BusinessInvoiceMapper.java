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
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.catalog.api.Currency;

public class BusinessInvoiceMapper implements ResultSetMapper<BusinessInvoice> {
    @Override
    public BusinessInvoice map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID invoiceId = UUID.fromString(r.getString(1));
        final DateTime createdDate = new DateTime(r.getLong(2), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong(3), DateTimeZone.UTC);
        final String accountKey = r.getString(4);
        final DateTime invoiceDate = new DateTime(r.getLong(5), DateTimeZone.UTC);
        final DateTime targetDate = new DateTime(r.getLong(6), DateTimeZone.UTC);
        final Currency currency = Currency.valueOf(r.getString(7));
        final BigDecimal balance = BigDecimal.valueOf(r.getDouble(8));
        final BigDecimal amountPaid = BigDecimal.valueOf(r.getDouble(9));
        final BigDecimal amountCharged = BigDecimal.valueOf(r.getDouble(10));
        final BigDecimal amountCredited = BigDecimal.valueOf(r.getDouble(11));

        return new BusinessInvoice(accountKey, amountCharged, amountCredited, amountPaid, balance, createdDate, currency,
                                   invoiceDate, invoiceId, targetDate, updatedDate);
    }
}
