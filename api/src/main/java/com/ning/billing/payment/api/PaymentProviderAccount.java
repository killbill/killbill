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

import com.google.common.base.Objects;

public class PaymentProviderAccount {
    private final String id;
    private final String accountNumber;
    private final String accountName;
    private final String phoneNumber;
    private final String defaultPaymentMethodId;

    public PaymentProviderAccount(String id,
                                  String accountNumber,
                                  String accountName,
                                  String phoneNumber,
                                  String defaultPaymentMethodId) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.phoneNumber = phoneNumber;
        this.defaultPaymentMethodId = defaultPaymentMethodId;
    }

    public String getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getDefaultPaymentMethodId() {
        return defaultPaymentMethodId;
    }

    public static class Builder {
        private String id;
        private String accountNumber;
        private String accountName;
        private String phoneNumber;
        private String defaultPaymentMethodId;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder setAccountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder setDefaultPaymentMethod(String defaultPaymentMethod) {
            this.defaultPaymentMethodId = defaultPaymentMethod;
            return this;
        }

        public PaymentProviderAccount build() {
            return new PaymentProviderAccount(id, accountNumber, accountName, phoneNumber, defaultPaymentMethodId);
        }

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id,
                                accountNumber,
                                accountName,
                                phoneNumber,
                                defaultPaymentMethodId);
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PaymentProviderAccount other = (PaymentProviderAccount)obj;
            if (obj == other) {
                return true;
            }
            else {
                return Objects.equal(id, other.id) &&
                       Objects.equal(accountNumber, other.accountNumber) &&
                       Objects.equal(phoneNumber, other.phoneNumber) &&
                       Objects.equal(defaultPaymentMethodId, other.defaultPaymentMethodId);
            }
        }
        return false;
    }

}
