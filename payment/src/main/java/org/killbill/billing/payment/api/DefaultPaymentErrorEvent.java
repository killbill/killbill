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

package org.killbill.billing.payment.api;

import java.util.UUID;

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.PaymentErrorInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultPaymentErrorEvent extends BusEventBase implements PaymentErrorInternalEvent {

    private final String message;
    private final UUID accountId;
    private final UUID paymentId;
    private final TransactionType transactionType;

    @JsonCreator
    public DefaultPaymentErrorEvent(@JsonProperty("accountId") final UUID accountId,
                                    @JsonProperty("paymentId") final UUID paymentId,
                                    @JsonProperty("transactionType")  final TransactionType transactionType,
                                    @JsonProperty("message") final String message,
                                    @JsonProperty("searchKey1") final Long searchKey1,
                                    @JsonProperty("searchKey2") final Long searchKey2,
                                    @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.message = message;
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.transactionType = transactionType;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.PAYMENT_ERROR;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPaymentErrorEvent{");
        sb.append("message='").append(message).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", transactionType=").append(transactionType);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPaymentErrorEvent)) {
            return false;
        }

        final DefaultPaymentErrorEvent that = (DefaultPaymentErrorEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (transactionType != null ? !transactionType.equals(that.transactionType) : that.transactionType != null) {
            return false;
        }
        if (message != null ? !message.equals(that.message) : that.message != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        return result;
    }
}
