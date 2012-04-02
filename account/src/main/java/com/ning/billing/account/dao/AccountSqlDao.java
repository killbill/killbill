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

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.entity.MapperBase;
import com.ning.billing.util.entity.UpdatableEntityDao;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.user.AccountBuilder;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.UuidMapper;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper({UuidMapper.class, AccountSqlDao.AccountMapper.class})
public interface AccountSqlDao extends UpdatableEntityDao<Account>, Transactional<AccountSqlDao>, Transmogrifier {
    @SqlQuery
    public Account getAccountByKey(@Bind("externalKey") final String key);

    @SqlQuery
    public UUID getIdFromKey(@Bind("externalKey") final String key);

    @Override
    @SqlUpdate
    public void create(@AccountBinder Account account, @CallContextBinder final CallContext context);

    @Override
    @SqlUpdate
    public void update(@AccountBinder Account account, @CallContextBinder final CallContext context);

    @SqlUpdate
    public void deleteByKey(@Bind("externalKey") final String key);

    public static class AccountMapper extends MapperBase implements ResultSetMapper<Account> {
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

            String paymentProviderName = result.getString("payment_provider_name");

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

            String createdBy = result.getString("created_by");
            DateTime createdDate = getDate(result, "created_date");
            String updatedBy = result.getString("updated_by");
            DateTime updatedDate = getDate(result, "updated_date");

            return new AccountBuilder(id).externalKey(externalKey).email(email)
                                         .name(name).firstNameLength(firstNameLength)
                                         .phone(phone).currency(currency)
                                         .billingCycleDay(billingCycleDay)
                                         .paymentProviderName(paymentProviderName)
                                         .timeZone(timeZone).locale(locale)
                                         .address1(address1).address2(address2)
                                         .companyName(companyName)
                                         .city(city).stateOrProvince(stateOrProvince)
                                         .postalCode(postalCode).country(country)
                                         .createdBy(createdBy).createdDate(createdDate)
                                         .updatedBy(updatedBy).updatedDate(updatedDate)
                                         .build();
        }
    }
}
