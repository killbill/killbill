/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;

public class DefaultDirectPaymentTransaction extends EntityBase implements DirectPaymentTransaction {

    private final UUID directPaymentId;
    private final String externalKey;
    private final TransactionType transactionType;
    private final DateTime effectiveDate;
    private final TransactionStatus status;
    private final BigDecimal amount;
    private final Currency currency;
    private final BigDecimal processedAmount;
    private final Currency processedCurrency;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    private final PaymentTransactionInfoPlugin infoPlugin;

    public DefaultDirectPaymentTransaction(final UUID id, final String externalKey, final DateTime createdDate, final DateTime updatedDate, final UUID directPaymentId, final TransactionType transactionType,
                                           final DateTime effectiveDate, final TransactionStatus status, final BigDecimal amount, final Currency currency, final BigDecimal processedAmount, final Currency processedCurrency,
                                           final String gatewayErrorCode, final String gatewayErrorMsg, final PaymentTransactionInfoPlugin infoPlugin) {
        super(id, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.directPaymentId = directPaymentId;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.processedAmount = processedAmount;
        this.processedCurrency = processedCurrency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.infoPlugin = infoPlugin;
    }

    @Override
    public UUID getDirectPaymentId() {
        return directPaymentId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
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
    public BigDecimal getProcessedAmount() {
        return processedAmount;
    }

    @Override
    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    @Override
    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    @Override
    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    @Override
    public TransactionStatus getTransactionStatus() {
        return status;
    }

    @Override
    public PaymentTransactionInfoPlugin getPaymentInfoPlugin() {
        return infoPlugin;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultDirectPaymentTransaction{");
        sb.append("directPaymentId=").append(directPaymentId);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", transactionType=").append(transactionType);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", status=").append(status);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", gatewayErrorCode='").append(gatewayErrorCode).append('\'');
        sb.append(", gatewayErrorMsg='").append(gatewayErrorMsg).append('\'');
        sb.append(", infoPlugin=").append(infoPlugin);
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

        final DefaultDirectPaymentTransaction that = (DefaultDirectPaymentTransaction) o;

        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (directPaymentId != null ? !directPaymentId.equals(that.directPaymentId) : that.directPaymentId != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (infoPlugin != null ? !infoPlugin.equals(that.infoPlugin) : that.infoPlugin != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }
        if (transactionType != that.transactionType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (directPaymentId != null ? directPaymentId.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (infoPlugin != null ? infoPlugin.hashCode() : 0);
        return result;
    }
}
