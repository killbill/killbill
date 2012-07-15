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

package com.ning.billing.account.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultBillCycleDay;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.MapperBase;

public class AccountMapper extends MapperBase implements ResultSetMapper<Account> {
    @Override
    public Account map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
        final UUID id = UUID.fromString(result.getString("id"));
        final String externalKey = result.getString("external_key");
        final String email = result.getString("email");
        final String name = result.getString("name");
        final int firstNameLength = result.getInt("first_name_length");
        final int billingCycleDayLocal = result.getInt("billing_cycle_day_local");
        final int billingCycleDayUTC = result.getInt("billing_cycle_day_utc");

        final String currencyString = result.getString("currency");
        final Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);

        final UUID paymentMethodId = result.getString("payment_method_id") != null ? UUID.fromString(result.getString("payment_method_id")) : null;

        final String timeZoneId = result.getString("time_zone");
        final DateTimeZone timeZone = (timeZoneId == null) ? null : DateTimeZone.forID(timeZoneId);

        final String locale = result.getString("locale");

        final String address1 = result.getString("address1");
        final String address2 = result.getString("address2");
        final String companyName = result.getString("company_name");
        final String city = result.getString("city");
        final String stateOrProvince = result.getString("state_or_province");
        final String postalCode = result.getString("postal_code");
        final String country = result.getString("country");
        final String phone = result.getString("phone");

        final Boolean isMigrated = result.getBoolean("migrated");
        final Boolean isNotifiedForInvoices = result.getBoolean("is_notified_for_invoices");

        return new DefaultAccount(id, externalKey, email, name, firstNameLength, currency,
                                  new DefaultBillCycleDay(billingCycleDayLocal, billingCycleDayUTC), paymentMethodId, timeZone, locale,
                                  address1, address2, companyName, city, stateOrProvince, country, postalCode, phone,
                                  isMigrated, isNotifiedForInvoices);
    }
}
