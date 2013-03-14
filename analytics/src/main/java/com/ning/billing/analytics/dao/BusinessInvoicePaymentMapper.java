/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.catalog.api.Currency;

public class BusinessInvoicePaymentMapper implements ResultSetMapper<BusinessInvoicePaymentModelDao> {

    @Override
    public BusinessInvoicePaymentModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID paymentId = UUID.fromString(r.getString(1));
        final DateTime createdDate = new DateTime(r.getLong(2), DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(r.getLong(3), DateTimeZone.UTC);
        final String extFirstPaymentRefId = r.getString(4);
        final String extSecondPaymentRefId = r.getString(5);
        final String accountKey = r.getString(6);
        final UUID invoiceId = UUID.fromString(r.getString(7));
        final DateTime effectiveDate = new DateTime(r.getLong(8), DateTimeZone.UTC);
        final BigDecimal amount = BigDecimal.valueOf(r.getDouble(9));
        final Currency currency = Currency.valueOf(r.getString(10));
        final String paymentError = r.getString(11);
        final String processingStatus = r.getString(12);
        final BigDecimal requestedAmount = BigDecimal.valueOf(r.getDouble(13));
        final String pluginName = r.getString(14);
        final String paymentType = r.getString(15);
        final String paymentMethod = r.getString(16);
        final String cardType = r.getString(17);
        final String cardCountry = r.getString(18);
        final String invoicePaymentType = r.getString(19);
        final String linkedInvoicePaymentIdString = r.getString(20);

        final UUID linkedInvoicePaymentId;
        if (linkedInvoicePaymentIdString != null) {
            linkedInvoicePaymentId = UUID.fromString(linkedInvoicePaymentIdString);
        } else {
            linkedInvoicePaymentId = null;
        }

        return new BusinessInvoicePaymentModelDao(accountKey, amount, cardCountry, cardType, createdDate, currency,
                                                  effectiveDate, invoiceId, paymentError, paymentId, paymentMethod, paymentType,
                                                  pluginName, processingStatus, requestedAmount, updatedDate, invoicePaymentType,
                                                  linkedInvoicePaymentId);
    }
}
