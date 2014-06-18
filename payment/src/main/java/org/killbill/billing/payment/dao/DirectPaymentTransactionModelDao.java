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

import com.google.common.base.Objects;

public class DirectPaymentTransactionModelDao extends EntityBase implements EntityModelDao<DirectPaymentTransaction> {

    private UUID directPaymentId;
    private String transactionExternalKey;
    private TransactionType transactionType;
    private DateTime effectiveDate;
    private PaymentStatus paymentStatus;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal processedAmount;
    private Currency processedCurrency;
    private String gatewayErrorCode;
    private String gatewayErrorMsg;
    private String extFirstPaymentRefId;
    private String extSecondPaymentRefId;


    public DirectPaymentTransactionModelDao() { /* For the DAO mapper */ }

    public DirectPaymentTransactionModelDao(final UUID id, @Nullable final String transactionExternalKey, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                            final UUID directPaymentId, final TransactionType transactionType, final DateTime effectiveDate,
                                            final PaymentStatus paymentStatus, final BigDecimal amount, final Currency currency, final String gatewayErrorCode, final String gatewayErrorMsg,
                                            @Nullable  final String extFirstPaymentRefId, @Nullable final String extSecondPaymentRefId) {
        super(id, createdDate, updatedDate);
        this.transactionExternalKey = Objects.firstNonNull(transactionExternalKey, id.toString());
        this.directPaymentId = directPaymentId;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.paymentStatus = paymentStatus;
        this.amount = amount;
        this.currency = currency;
        this.processedAmount = null;
        this.processedCurrency = null;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.extFirstPaymentRefId = extFirstPaymentRefId;
        this.extSecondPaymentRefId = extSecondPaymentRefId;
    }

    public DirectPaymentTransactionModelDao(@Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                            @Nullable final String transactionExternalKey, final UUID directPaymentId, final TransactionType transactionType, final DateTime effectiveDate,
                                            final PaymentStatus paymentStatus, final BigDecimal amount, final Currency currency, final String gatewayErrorCode, final String gatewayErrorMsg) {
        this(UUID.randomUUID(), transactionExternalKey, createdDate, updatedDate, directPaymentId, transactionType, effectiveDate, paymentStatus, amount, currency, gatewayErrorCode, gatewayErrorMsg, null, null);
    }

    public UUID getDirectPaymentId() {
        return directPaymentId;
    }

    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getProcessedAmount() {
        return processedAmount;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public void setDirectPaymentId(final UUID directPaymentId) {
        this.directPaymentId = directPaymentId;
    }

    public void setTransactionExternalKey(final String transactionExternalKey) {
        this.transactionExternalKey = transactionExternalKey;
    }

    public void setTransactionType(final TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public void setPaymentStatus(final PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public void setProcessedAmount(final BigDecimal processedAmount) {
        this.processedAmount = processedAmount;
    }

    public void setProcessedCurrency(final Currency processedCurrency) {
        this.processedCurrency = processedCurrency;
    }

    public void setGatewayErrorCode(final String gatewayErrorCode) {
        this.gatewayErrorCode = gatewayErrorCode;
    }

    public void setGatewayErrorMsg(final String gatewayErrorMsg) {
        this.gatewayErrorMsg = gatewayErrorMsg;
    }

    public String getExtFirstPaymentRefId() {
        return extFirstPaymentRefId;
    }

    public String getExtSecondPaymentRefId() {
        return extSecondPaymentRefId;
    }

    public void setExtFirstPaymentRefId(final String extFirstPaymentRefId) {
        this.extFirstPaymentRefId = extFirstPaymentRefId;
    }

    public void setExtSecondPaymentRefId(final String extSecondPaymentRefId) {
        this.extSecondPaymentRefId = extSecondPaymentRefId;
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
        if (transactionExternalKey != null ? !transactionExternalKey.equals(that.transactionExternalKey) : that.transactionExternalKey != null) {
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
        if (processedAmount != null ? processedAmount.compareTo(that.processedAmount) != 0 : that.processedAmount != null) {
            return false;
        }
        if (processedCurrency != that.processedCurrency) {
            return false;
        }
        if (transactionType != that.transactionType) {
            return false;
        }
        if (extFirstPaymentRefId != null ? !extFirstPaymentRefId.equals(that.extFirstPaymentRefId) : that.extFirstPaymentRefId != null) {
            return false;
        }
        if (extSecondPaymentRefId != null ? !extSecondPaymentRefId.equals(that.extSecondPaymentRefId) : that.extSecondPaymentRefId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (directPaymentId != null ? directPaymentId.hashCode() : 0);
        result = 31 * result + (transactionExternalKey != null ? transactionExternalKey.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (paymentStatus != null ? paymentStatus.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (processedAmount != null ? processedAmount.hashCode() : 0);
        result = 31 * result + (processedCurrency != null ? processedCurrency.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (extFirstPaymentRefId != null ? extFirstPaymentRefId.hashCode() : 0);
        result = 31 * result + (extSecondPaymentRefId != null ? extSecondPaymentRefId.hashCode() : 0);
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
