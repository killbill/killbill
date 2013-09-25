/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.payment.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.events.BusEventBase;
import com.ning.billing.events.PaymentPluginErrorInternalEvent;

import java.util.UUID;

public class DefaultPaymentPluginErrorEvent extends BusEventBase implements PaymentPluginErrorInternalEvent {

    private final String message;
    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentId;

    @JsonCreator
    public DefaultPaymentPluginErrorEvent(@JsonProperty("accountId") final UUID accountId,
                                          @JsonProperty("invoiceId") final UUID invoiceId,
                                          @JsonProperty("paymentId") final UUID paymentId,
                                          @JsonProperty("message") final String message,
                                          @JsonProperty("searchKey1") final Long searchKey1,
                                          @JsonProperty("searchKey2") final Long searchKey2,
                                          @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.message = message;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
    }

    public String getMessage() {
        return message;
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

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.PAYMENT_ERROR;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPaymentPluginErrorEvent)) {
            return false;
        }

        final DefaultPaymentPluginErrorEvent that = (DefaultPaymentPluginErrorEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
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
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        return result;
    }
}
