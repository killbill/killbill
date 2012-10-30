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

import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.MapperBase;

public class BusinessInvoiceItemMapper extends MapperBase implements ResultSetMapper<BusinessInvoiceItemModelDao> {

    @Override
    public BusinessInvoiceItemModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID itemId = getUUID(r, "item_id");
        final UUID linkedItemId = getUUID(r, "linked_item_id");
        final DateTime createdDate = new DateTime(r.getLong("created_date"), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong("updated_date"), DateTimeZone.UTC);
        final UUID invoiceId = getUUID(r, "invoice_id");
        final String itemType = r.getString("item_type");
        final String externalKey = r.getString("external_key");
        final String productName = r.getString("product_name");
        final String productType = r.getString("product_type");
        final String productCategory = r.getString("product_category");
        final String slug = r.getString("slug");
        final String phase = r.getString("phase");
        final String billingPeriod = r.getString("billing_period");
        final LocalDate startDate = getDate(r, "start_date");
        final LocalDate endDate = getDate(r, "end_date");
        final BigDecimal amount = BigDecimal.valueOf(r.getDouble("amount"));
        final Currency currency = Currency.valueOf(r.getString("currency"));

        return new BusinessInvoiceItemModelDao(amount, billingPeriod, createdDate, currency, endDate, externalKey, invoiceId,
                                       itemId, linkedItemId, itemType, phase, productCategory, productName, productType, slug,
                                       startDate, updatedDate);
    }
}
