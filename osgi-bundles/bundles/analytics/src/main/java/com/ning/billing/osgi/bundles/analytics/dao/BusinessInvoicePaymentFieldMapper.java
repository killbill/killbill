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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.osgi.bundles.analytics.model.BusinessInvoicePaymentFieldModelDao;

public class BusinessInvoicePaymentFieldMapper implements ResultSetMapper<BusinessInvoicePaymentFieldModelDao> {

    @Override
    public BusinessInvoicePaymentFieldModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        return new BusinessInvoicePaymentFieldModelDao(UUID.fromString(r.getString(1)), r.getString(2), r.getString(3));
    }
}
