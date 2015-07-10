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

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.PaymentInfoInternalEvent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultPaymentInfoEvent extends DefaultPaymentInternalEvent implements PaymentInfoInternalEvent {

    public DefaultPaymentInfoEvent(@JsonProperty("accountId") final UUID accountId,
                                   @JsonProperty("paymentId") final UUID paymentId,
                                   @JsonProperty("paymentTransactionId") final UUID paymentTransactionId,
                                   @JsonProperty("amount") final BigDecimal amount,
                                   @JsonProperty("currency") final Currency currency,
                                   @JsonProperty("status") final TransactionStatus status,
                                   @JsonProperty("transactionType") final TransactionType transactionType,
                                   @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                   @JsonProperty("searchKey1") final Long searchKey1,
                                   @JsonProperty("searchKey2") final Long searchKey2,
                                   @JsonProperty("userToken") final UUID userToken) {
        super(accountId, paymentId, paymentTransactionId, amount, currency, status, transactionType, effectiveDate, searchKey1, searchKey2, userToken);
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.PAYMENT_INFO;
    }


    @Override
    protected Class getPaymentInternalEventClass() {
        return DefaultPaymentInfoEvent.class;
    }
}
