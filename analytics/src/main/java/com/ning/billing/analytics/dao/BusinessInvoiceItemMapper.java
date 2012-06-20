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

import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.catalog.api.Currency;

public class BusinessInvoiceItemMapper implements ResultSetMapper<BusinessInvoiceItem> {
    @Override
    public BusinessInvoiceItem map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID itemId = UUID.fromString(r.getString(1));
        final DateTime createdDate = new DateTime(r.getLong(2), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong(3), DateTimeZone.UTC);
        final UUID invoiceId = UUID.fromString(r.getString(4));
        final String itemType = r.getString(5);
        final String externalKey = r.getString(6);
        final String productName = r.getString(7);
        final String productType = r.getString(8);
        final String productCategory = r.getString(9);
        final String slug = r.getString(10);
        final String phase = r.getString(11);
        final String billingPeriod = r.getString(12);
        final DateTime startDate = new DateTime(r.getLong(13), DateTimeZone.UTC);
        final DateTime endDate = new DateTime(r.getLong(14), DateTimeZone.UTC);
        final BigDecimal amount = BigDecimal.valueOf(r.getDouble(15));
        final Currency currency = Currency.valueOf(r.getString(16));

        return new BusinessInvoiceItem(amount, billingPeriod, createdDate, currency, endDate, externalKey, invoiceId,
                                       itemId, itemType, phase, productCategory, productName, productType, slug,
                                       startDate, updatedDate);
    }
}
