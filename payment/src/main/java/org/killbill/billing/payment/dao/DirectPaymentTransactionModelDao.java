/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class DirectPaymentTransactionModelDao extends EntityBase implements EntityModelDao<DirectPaymentTransaction> {

    private UUID directPaymentId;
    private TransactionType transactionType;
    private DateTime effectiveDate;
    private PaymentStatus paymentStatus;
    private BigDecimal amount;
    private Currency currency;
    private String gatewayErrorCode;
    private String gatewayErrorMsg;

    public DirectPaymentTransactionModelDao() { /* For the DAO mapper */ }

    public DirectPaymentTransactionModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                            final UUID directPaymentId, final TransactionType transactionType, final DateTime effectiveDate,
                                            final PaymentStatus paymentStatus, final BigDecimal amount, final Currency currency, final String gatewayErrorCode, final String gatewayErrorMsg) {
        super(id, createdDate, updatedDate);
        this.directPaymentId = directPaymentId;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.paymentStatus = paymentStatus;
        this.amount = amount;
        this.currency = currency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
    }

    public DirectPaymentTransactionModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                            final UUID directPaymentId, final TransactionType transactionType, final DateTime effectiveDate,
                                            final PaymentStatus paymentStatus, final BigDecimal amount, final Currency currency, final String gatewayErrorCode, final String gatewayErrorMsg) {
        this(UUID.randomUUID(), createdDate, updatedDate, directPaymentId, transactionType, effectiveDate, paymentStatus, amount, currency, gatewayErrorCode, gatewayErrorMsg);
    }

        public UUID getDirectPaymentId() {
        return directPaymentId;
    }

    public void setDirectPaymentId(final UUID directPaymentId) {
        this.directPaymentId = directPaymentId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(final TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(final PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public void setGatewayErrorCode(final String gatewayErrorCode) {
        this.gatewayErrorCode = gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public void setGatewayErrorMsg(final String gatewayErrorMsg) {
        this.gatewayErrorMsg = gatewayErrorMsg;
    }

    @Override
    public String toString() {
        return "DirectPaymentTransactionModelDao{" +
               "directPaymentId=" + directPaymentId +
               ", transactionType=" + transactionType +
               ", effectiveDate=" + effectiveDate +
               ", paymentStatus=" + paymentStatus +
               ", amount=" + amount +
               ", currency=" + currency +
               ", gatewayErrorCode='" + gatewayErrorCode + '\'' +
               ", gatewayErrorMsg='" + gatewayErrorMsg + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectPaymentTransactionModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DirectPaymentTransactionModelDao that = (DirectPaymentTransactionModelDao) o;

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
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (paymentStatus != that.paymentStatus) {
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
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (paymentStatus != null ? paymentStatus.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.DIRECT_TRANSACTIONS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.DIRECT_TRANSACTION_HISTORY;
    }
}
