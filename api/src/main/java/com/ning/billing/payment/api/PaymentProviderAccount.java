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

}
