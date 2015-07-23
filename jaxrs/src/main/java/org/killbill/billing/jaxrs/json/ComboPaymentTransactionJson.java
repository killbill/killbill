/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ComboPaymentTransactionJson extends JsonBase {

    private final AccountJson account;
    private final PaymentMethodJson paymentMethod;
    private final PaymentTransactionJson transaction;

    @JsonCreator
    public ComboPaymentTransactionJson(@JsonProperty("account") final AccountJson account,
                                       @JsonProperty("paymentMethod") final PaymentMethodJson paymentMethod,
                                       @JsonProperty("transaction") final PaymentTransactionJson transaction,
                                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.account = account;
        this.paymentMethod = paymentMethod;
        this.transaction = transaction;
    }

    public AccountJson getAccount() {
        return account;
    }

    public PaymentMethodJson getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentTransactionJson getTransaction() {
        return transaction;
    }

    @Override
    public String toString() {
        return "ComboPaymentTransactionJson{" +
               "account=" + account +
               ", paymentMethod=" + paymentMethod +
               ", transaction=" + transaction +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComboPaymentTransactionJson)) {
            return false;
        }

        final ComboPaymentTransactionJson that = (ComboPaymentTransactionJson) o;

        if (account != null ? !account.equals(that.account) : that.account != null) {
            return false;
        }
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null) {
            return false;
        }
        return !(transaction != null ? !transaction.equals(that.transaction) : that.transaction != null);

    }

    @Override
    public int hashCode() {
        int result = account != null ? account.hashCode() : 0;
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (transaction != null ? transaction.hashCode() : 0);
        return result;
    }
}
