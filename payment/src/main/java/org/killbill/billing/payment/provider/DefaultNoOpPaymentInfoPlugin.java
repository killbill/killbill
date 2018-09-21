/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;

import com.google.common.collect.ImmutableList;

public class DefaultNoOpPaymentInfoPlugin implements PaymentTransactionInfoPlugin {

    private final UUID kbPaymentId;
    private final UUID kbTransactionPaymentId;
    private final BigDecimal amount;
    private final DateTime effectiveDate;
    private final DateTime createdDate;
    private final PaymentPluginStatus status;
    private final String gatewayError;
    private final String gatewayErrorCode;
    private final Currency currency;
    private final TransactionType transactionType;
    private final String firstPaymentReferenceId;
    private final String secondPaymentReferenceId;
    private final List<PluginProperty> pluginProperties;

    public DefaultNoOpPaymentInfoPlugin(final UUID kbPaymentId, final UUID kbTransactionPaymentId, final TransactionType transactionType, final BigDecimal amount, final Currency currency, final DateTime effectiveDate,
                                        final DateTime createdDate, final PaymentPluginStatus status, final String gatewayErrorCode, final String gatewayError) {
        this(kbPaymentId, kbTransactionPaymentId, transactionType, amount, currency, effectiveDate, createdDate, status, gatewayErrorCode, gatewayError, null, null, ImmutableList.<PluginProperty>of());
    }

    public DefaultNoOpPaymentInfoPlugin(final UUID kbPaymentId,
                                        final UUID kbTransactionPaymentId,
                                        final TransactionType transactionType,
                                        final BigDecimal amount,
                                        final Currency currency,
                                        final DateTime effectiveDate,
                                        final DateTime createdDate,
                                        final PaymentPluginStatus status,
                                        final String gatewayErrorCode,
                                        final String gatewayError,
                                        final String firstPaymentReferenceId,
                                        final String secondPaymentReferenceId,
                                        final List<PluginProperty> pluginProperties) {
        this.kbPaymentId = kbPaymentId;
        this.kbTransactionPaymentId = kbTransactionPaymentId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.effectiveDate = effectiveDate;
        this.createdDate = createdDate;
        this.status = status;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayError = gatewayError;
        this.currency = currency;
        this.firstPaymentReferenceId = firstPaymentReferenceId;
        this.secondPaymentReferenceId = secondPaymentReferenceId;
        this.pluginProperties = pluginProperties;
    }

    @Override
    public UUID getKbPaymentId() {
        return kbPaymentId;
    }

    @Override
    public UUID getKbTransactionPaymentId() {
        return kbTransactionPaymentId;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public PaymentPluginStatus getStatus() {
        return status;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public String getGatewayError() {
        return gatewayError;
    }

    @Override
    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    @Override
    public String getFirstPaymentReferenceId() {
        return firstPaymentReferenceId;
    }

    @Override
    public String getSecondPaymentReferenceId() {
        return secondPaymentReferenceId;
    }

    @Override
    public List<PluginProperty> getProperties() {
        return pluginProperties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultNoOpPaymentInfoPlugin{");
        sb.append("kbPaymentId=").append(kbPaymentId);
        sb.append(", kbTransactionPaymentId=").append(kbTransactionPaymentId);
        sb.append(", amount=").append(amount);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", status=").append(status);
        sb.append(", gatewayError='").append(gatewayError).append('\'');
        sb.append(", gatewayErrorCode='").append(gatewayErrorCode).append('\'');
        sb.append(", currency=").append(currency);
        sb.append(", transactionType=").append(transactionType);
        sb.append(", firstPaymentReferenceId='").append(firstPaymentReferenceId).append('\'');
        sb.append(", secondPaymentReferenceId='").append(secondPaymentReferenceId).append('\'');
        sb.append(", pluginProperties=").append(pluginProperties);
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

        final DefaultNoOpPaymentInfoPlugin that = (DefaultNoOpPaymentInfoPlugin) o;

        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (gatewayError != null ? !gatewayError.equals(that.gatewayError) : that.gatewayError != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (transactionType != null ? !transactionType.equals(that.transactionType) : that.transactionType != null) {
            return false;
        }
        if (kbPaymentId != null ? !kbPaymentId.equals(that.kbPaymentId) : that.kbPaymentId != null) {
            return false;
        }
        if (kbTransactionPaymentId != null ? !kbTransactionPaymentId.equals(that.kbTransactionPaymentId) : that.kbTransactionPaymentId != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }
        if (firstPaymentReferenceId != null ? !firstPaymentReferenceId.equals(that.firstPaymentReferenceId) : that.firstPaymentReferenceId != null) {
            return false;
        }
        if (secondPaymentReferenceId != null ? !secondPaymentReferenceId.equals(that.secondPaymentReferenceId) : that.secondPaymentReferenceId != null) {
            return false;
        }
        if (pluginProperties != null ? !pluginProperties.equals(that.pluginProperties) : that.pluginProperties != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = kbPaymentId != null ? kbPaymentId.hashCode() : 0;
        result = 31 * result + (kbTransactionPaymentId != null ? kbTransactionPaymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (gatewayError != null ? gatewayError.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (firstPaymentReferenceId != null ? firstPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (secondPaymentReferenceId != null ? secondPaymentReferenceId.hashCode() : 0);
        result = 31 * result + (pluginProperties != null ? pluginProperties.hashCode() : 0);
        return result;
    }
}
