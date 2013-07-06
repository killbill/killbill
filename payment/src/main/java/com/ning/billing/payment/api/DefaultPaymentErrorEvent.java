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

package com.ning.billing.payment.api;

import java.util.UUID;

import com.ning.billing.util.events.PaymentErrorInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "error")
public class DefaultPaymentErrorEvent implements PaymentErrorInternalEvent {

    private final UUID id;
    private final String message;
    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentId;


    @JsonCreator
    public DefaultPaymentErrorEvent(@JsonProperty("id") final UUID id, /* not used */
                                    @JsonProperty("accountId") final UUID accountId,
                                    @JsonProperty("invoiceId") final UUID invoiceId,
                                    @JsonProperty("paymentId") final UUID paymentId,
                                    @JsonProperty("message") final String message) {
        this.id = id;
        this.message = message;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
    }


    public DefaultPaymentErrorEvent(final UUID accountId,
                                    final UUID invoiceId, final UUID paymentId, final String message) {
        this(UUID.randomUUID(), accountId, invoiceId, paymentId, message);
    }


    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.PAYMENT_ERROR;
    }


    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                 + ((invoiceId == null) ? 0 : invoiceId.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result
                 + ((paymentId == null) ? 0 : paymentId.hashCode());
        return result;
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultPaymentErrorEvent other = (DefaultPaymentErrorEvent) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (invoiceId == null) {
            if (other.invoiceId != null) {
                return false;
            }
        } else if (!invoiceId.equals(other.invoiceId)) {
            return false;
        }
        if (message == null) {
            if (other.message != null) {
                return false;
            }
        } else if (!message.equals(other.message)) {
            return false;
        }
        if (paymentId == null) {
            if (other.paymentId != null) {
                return false;
            }
        } else if (!paymentId.equals(other.paymentId)) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "DefaultPaymentErrorEvent [message=" + message + ", accountId="
               + accountId + ", invoiceId=" + invoiceId + ", paymentId="
               + paymentId + "]";
    }
}
