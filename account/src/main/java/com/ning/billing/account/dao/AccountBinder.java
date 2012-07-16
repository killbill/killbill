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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;

@BindingAnnotation(AccountBinder.AccountBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface AccountBinder {
    public static class AccountBinderFactory implements BinderFactory {
        @Override
        public Binder<AccountBinder, Account> build(final Annotation annotation) {
            return new Binder<AccountBinder, Account>() {
                @Override
                public void bind(@SuppressWarnings("rawtypes") final SQLStatement q, final AccountBinder bind, final Account account) {
                    q.bind("id", account.getId().toString());
                    q.bind("externalKey", account.getExternalKey());
                    q.bind("email", account.getEmail());
                    q.bind("name", account.getName());
                    q.bind("firstNameLength", account.getFirstNameLength());
                    final Currency currency = account.getCurrency();
                    q.bind("currency", (currency == null) ? null : currency.toString());
                    q.bind("billingCycleDayLocal", account.getBillCycleDay().getDayOfMonthLocal());
                    q.bind("billingCycleDayUTC", account.getBillCycleDay().getDayOfMonthUTC());
                    q.bind("paymentMethodId", account.getPaymentMethodId() != null ? account.getPaymentMethodId().toString() : null);
                    final DateTimeZone timeZone = account.getTimeZone();
                    q.bind("timeZone", (timeZone == null) ? null : timeZone.toString());
                    q.bind("locale", account.getLocale());
                    q.bind("address1", account.getAddress1());
                    q.bind("address2", account.getAddress2());
                    q.bind("companyName", account.getCompanyName());
                    q.bind("city", account.getCity());
                    q.bind("stateOrProvince", account.getStateOrProvince());
                    q.bind("country", account.getCountry());
                    q.bind("postalCode", account.getPostalCode());
                    q.bind("phone", account.getPhone());
                    q.bind("migrated", account.isMigrated());
                    q.bind("isNotifiedForInvoices", account.isNotifiedForInvoices());
                }
            };
        }
    }
}
