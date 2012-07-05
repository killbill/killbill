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

import com.ning.billing.analytics.model.BusinessInvoicePayment;
import com.ning.billing.catalog.api.Currency;

public class BusinessInvoicePaymentMapper implements ResultSetMapper<BusinessInvoicePayment> {
    @Override
    public BusinessInvoicePayment map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID paymentId = UUID.fromString(r.getString(1));
        final DateTime createdDate = new DateTime(r.getLong(2), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong(3), DateTimeZone.UTC);
        final String extPaymentRefId = r.getString(4);
        final String accountKey = r.getString(5);
        final UUID invoiceId = UUID.fromString(r.getString(6));
        final DateTime effectiveDate = new DateTime(r.getLong(7), DateTimeZone.UTC);
        final BigDecimal amount = BigDecimal.valueOf(r.getDouble(8));
        final Currency currency = Currency.valueOf(r.getString(9));
        final String paymentError = r.getString(10);
        final String processingStatus = r.getString(11);
        final BigDecimal requestedAmount = BigDecimal.valueOf(r.getDouble(12));
        final String pluginName = r.getString(13);
        final String paymentType = r.getString(14);
        final String paymentMethod = r.getString(15);
        final String cardType = r.getString(16);
        final String cardCountry = r.getString(17);

        return new BusinessInvoicePayment(accountKey, amount, extPaymentRefId, cardCountry, cardType, createdDate, currency,
                                          effectiveDate, invoiceId, paymentError, paymentId, paymentMethod, paymentType,
                                          pluginName, processingStatus, requestedAmount, updatedDate);
    }
}
