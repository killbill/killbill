/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultInvoicePaymentErrorEvent extends DefaultInvoicePaymentInternalEvent implements InvoicePaymentErrorInternalEvent {

    public DefaultInvoicePaymentErrorEvent(@JsonProperty("accountId") final UUID accountId,
                                           @JsonProperty("paymentId") final UUID paymentId,
                                           @JsonProperty("type") final InvoicePaymentType type,
                                           @JsonProperty("invoiceId") final UUID invoiceId,
                                           @JsonProperty("paymentDate") final DateTime paymentDate,
                                           @JsonProperty("amount") final BigDecimal amount,
                                           @JsonProperty("currency") final Currency currency,
                                           @JsonProperty("linkedInvoicePaymentId") final UUID linkedInvoicePaymentId,
                                           @JsonProperty("paymentCookieId") final String paymentCookieId,
                                           @JsonProperty("processedCurrency") final Currency processedCurrency,
                                           @JsonProperty("searchKey1") final Long searchKey1,
                                           @JsonProperty("searchKey2") final Long searchKey2,
                                           @JsonProperty("userToken") final UUID userToken) {
        super(accountId, paymentId, type, invoiceId, paymentDate, amount, currency, linkedInvoicePaymentId, paymentCookieId, processedCurrency, searchKey1, searchKey2, userToken);
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.INVOICE_PAYMENT_ERROR;
    }

    @Override
    protected Class getInvoicePaymentInternalEventClass() {
        return DefaultInvoicePaymentErrorEvent.class;
    }
}
