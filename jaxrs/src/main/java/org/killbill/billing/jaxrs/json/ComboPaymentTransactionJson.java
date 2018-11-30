/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

import org.killbill.billing.payment.api.TransactionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="ComboPaymentTransaction", parent = ComboPaymentJson.class)
public class ComboPaymentTransactionJson extends ComboPaymentJson {

    private final PaymentTransactionJson transaction;
    private final List<PluginPropertyJson> transactionPluginProperties;

    @JsonCreator
    public ComboPaymentTransactionJson(@JsonProperty("account") final AccountJson account,
                                       @JsonProperty("paymentMethod") final PaymentMethodJson paymentMethod,
                                       @JsonProperty("transaction") final PaymentTransactionJson transaction,
                                       @JsonProperty("paymentMethodPluginProperties") final List<PluginPropertyJson> paymentMethodPluginProperties,
                                       @JsonProperty("transactionPluginProperties") final List<PluginPropertyJson> transactionPluginProperties,
                                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(account, paymentMethod, paymentMethodPluginProperties, auditLogs);
        this.transaction = transaction;
        this.transactionPluginProperties = transactionPluginProperties;
    }

    public PaymentTransactionJson getTransaction() {
        return transaction;
    }

    @JsonIgnore
    public TransactionType getTransactionType() {
        if (transaction != null) {
            return transaction.getTransactionType();
        }
        return null;
    }

    public List<PluginPropertyJson> getTransactionPluginProperties() {
        return transactionPluginProperties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ComboPaymentTransactionJson{");
        sb.append("transaction=").append(transaction);
        sb.append(", transactionPluginProperties=").append(transactionPluginProperties);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final ComboPaymentTransactionJson that = (ComboPaymentTransactionJson) o;

        if (transaction != null ? !transaction.equals(that.transaction) : that.transaction != null) {
            return false;
        }
        return !(transactionPluginProperties != null ? !transactionPluginProperties.equals(that.transactionPluginProperties) : that.transactionPluginProperties != null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (transaction != null ? transaction.hashCode() : 0);
        result = 31 * result + (transactionPluginProperties != null ? transactionPluginProperties.hashCode() : 0);
        return result;
    }
}
