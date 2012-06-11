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

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.MapperBase;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AccountMapper extends MapperBase implements ResultSetMapper<Account> {
    @Override
    public Account map(int index, ResultSet result, StatementContext context) throws SQLException {
        UUID id = UUID.fromString(result.getString("id"));
        String externalKey = result.getString("external_key");
        String email = result.getString("email");
        String name = result.getString("name");
        int firstNameLength = result.getInt("first_name_length");
        int billingCycleDay = result.getInt("billing_cycle_day");

        String currencyString = result.getString("currency");
        Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);

        UUID paymentMethodId = result.getString("payment_method_id") != null ? UUID.fromString(result.getString("payment_method_id")) : null;

        String timeZoneId = result.getString("time_zone");
        DateTimeZone timeZone = (timeZoneId == null) ? null : DateTimeZone.forID(timeZoneId);

        String locale = result.getString("locale");

        String address1 = result.getString("address1");
        String address2 = result.getString("address2");
        String companyName = result.getString("company_name");
        String city = result.getString("city");
        String stateOrProvince = result.getString("state_or_province");
        String postalCode = result.getString("postal_code");
        String country = result.getString("country");
        String phone = result.getString("phone");

        Boolean isMigrated = result.getBoolean("migrated");
        Boolean isNotifiedForInvoices = result.getBoolean("is_notified_for_invoices");

        return new DefaultAccount(id, externalKey, email, name,firstNameLength, currency,
                billingCycleDay, paymentMethodId, timeZone, locale,
                address1, address2, companyName, city, stateOrProvince, country, postalCode, phone,
                isMigrated, isNotifiedForInvoices);
    }
}