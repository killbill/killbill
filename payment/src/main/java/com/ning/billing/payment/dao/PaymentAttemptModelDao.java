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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class PaymentAttemptModelDao extends EntityBase implements EntityModelDao<PaymentAttempt> {

    private UUID accountId;
    private UUID invoiceId;
    private UUID paymentId;
    private PaymentStatus processingStatus;
    private DateTime effectiveDate;
    private String gatewayErrorCode;
    private String gatewayErrorMsg;
    private BigDecimal requestedAmount;

    public PaymentAttemptModelDao() { /* For the DAO mapper */ }

    public PaymentAttemptModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                  final UUID accountId, final UUID invoiceId,
                                  final UUID paymentId, final PaymentStatus processingStatus, final DateTime effectiveDate,
                                  final BigDecimal requestedAmount, final String gatewayErrorCode, final String gatewayErrorMsg) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.processingStatus = processingStatus;
        this.effectiveDate = effectiveDate;
        this.requestedAmount = requestedAmount;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
    }

    public PaymentAttemptModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentId, final PaymentStatus paymentStatus, final DateTime effectiveDate, final BigDecimal requestedAmount) {
        this(UUID.randomUUID(), null, null, accountId, invoiceId, paymentId, paymentStatus, effectiveDate, requestedAmount, null, null);
    }

    public PaymentAttemptModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentId, final DateTime effectiveDate, final BigDecimal requestedAmount) {
        this(UUID.randomUUID(), null, null, accountId, invoiceId, paymentId, PaymentStatus.UNKNOWN, effectiveDate, requestedAmount, null, null);
    }

    public PaymentAttemptModelDao(final PaymentAttemptModelDao src, final PaymentStatus newProcessingStatus, final String gatewayErrorCode, final String gatewayErrorMsg) {
        this(src.getId(), src.getCreatedDate(), src.getUpdatedDate(), src.getAccountId(), src.getInvoiceId(), src.getPaymentId(), newProcessingStatus,
             src.getEffectiveDate(), src.getRequestedAmount(), gatewayErrorCode, gatewayErrorMsg);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getProcessingStatus() {
        return processingStatus;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentAttemptModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", processingStatus=").append(processingStatus);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", gatewayErrorCode='").append(gatewayErrorCode).append('\'');
        sb.append(", gatewayErrorMsg='").append(gatewayErrorMsg).append('\'');
        sb.append(", requestedAmount=").append(requestedAmount);
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

        final PaymentAttemptModelDao that = (PaymentAttemptModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (processingStatus != that.processingStatus) {
            return false;
        }
        if (requestedAmount != null ? !requestedAmount.equals(that.requestedAmount) : that.requestedAmount != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (processingStatus != null ? processingStatus.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (requestedAmount != null ? requestedAmount.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.PAYMENT_ATTEMPTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.PAYMENT_ATTEMPT_HISTORY;
    }

}
