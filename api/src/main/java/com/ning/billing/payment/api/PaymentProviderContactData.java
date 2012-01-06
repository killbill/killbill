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

package com.ning.billing.payment.api;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Objects;
import com.ning.billing.catalog.api.Currency;

public class PaymentProviderContactData {
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phoneNumber;
    private final String externalKey;
    private final String locale;
    private final Currency currency;

    public PaymentProviderContactData(String firstName,
                                      String lastName,
                                      String email,
                                      String phoneNumber,
                                      String externalKey,
                                      String locale,
                                      Currency currency) {
        this.firstName = StringUtils.substring(firstName, 0, 100);
        this.lastName  = StringUtils.substring(lastName, 0, 100);
        this.email     = StringUtils.substring(email, 0, 80);
        this.phoneNumber = phoneNumber;
        this.externalKey = externalKey;
        this.locale = locale;
        this.currency = currency;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getLocale() {
        return locale;
    }

    public Currency getCurrency() {
        return currency;
    }

    public static class Builder {
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
        private String externalKey;
        private String locale;
        private Currency currency;

        public Builder setExternalKey(String externalKey) {
            this.externalKey = externalKey;
            return this;
        }

        public Builder setFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder setLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder setLocale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder setCurrency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public PaymentProviderContactData build() {
            return new PaymentProviderContactData(firstName, lastName, email, phoneNumber, externalKey, locale, currency);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(firstName,
                                lastName,
                                email,
                                phoneNumber,
                                externalKey,
                                locale,
                                currency);
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PaymentProviderContactData other = (PaymentProviderContactData)obj;
            if (obj == other) {
                return true;
            }
            else {
                return Objects.equal(firstName, other.firstName) &&
                       Objects.equal(lastName, other.lastName) &&
                       Objects.equal(email, other.email) &&
                       Objects.equal(phoneNumber, other.phoneNumber) &&
                       Objects.equal(externalKey, other.externalKey) &&
                       Objects.equal(locale, other.locale) &&
                       Objects.equal(currency, other.currency);
            }
        }
        return false;
    }

}
