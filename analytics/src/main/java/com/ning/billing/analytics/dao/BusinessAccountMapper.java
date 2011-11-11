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

package com.ning.billing.analytics.dao;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.ning.billing.analytics.BusinessAccount;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BusinessAccountMapper implements ResultSetMapper<BusinessAccount>
{
    private final Splitter splitter = Splitter.on(";").trimResults().omitEmptyStrings();

    @Override
    public BusinessAccount map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException
    {
        final List<String> tags = new ArrayList<String>();
        Iterables.addAll(tags, splitter.split(r.getString(5)));

        final BusinessAccount account = new BusinessAccount(
            r.getString(1),
            BigDecimal.valueOf(r.getDouble(4)),
            tags,
            new DateTime(r.getLong(6), DateTimeZone.UTC),
            BigDecimal.valueOf(r.getDouble(7)),
            r.getString(8),
            r.getString(9),
            r.getString(10)
        );
        account.setCreatedDt(new DateTime(r.getLong(2), DateTimeZone.UTC));
        account.setUpdatedDt(new DateTime(r.getLong(3), DateTimeZone.UTC));

        return account;
    }
}
