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

package org.killbill.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.InvoiceCreationInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultInvoiceCreationEvent extends BusEventBase implements InvoiceCreationInternalEvent {

    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amountOwed;
    private final Currency currency;

    @JsonCreator
    public DefaultInvoiceCreationEvent(@JsonProperty("invoiceId") final UUID invoiceId,
                                       @JsonProperty("accountId") final UUID accountId,
                                       @JsonProperty("amountOwed") final BigDecimal amountOwed,
                                       @JsonProperty("currency") final Currency currency,
                                       @JsonProperty("searchKey1") final Long searchKey1,
                                       @JsonProperty("searchKey2") final Long searchKey2,
                                       @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amountOwed = amountOwed;
        this.currency = currency;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.INVOICE_CREATION;
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
    public BigDecimal getAmountOwed() {
        return amountOwed;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return "DefaultInvoiceCreationNotification [invoiceId=" + invoiceId + ", accountId=" + accountId + ", amountOwed=" + amountOwed + ", currency=" + currency + "]";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultInvoiceCreationEvent that = (DefaultInvoiceCreationEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amountOwed != null ? !amountOwed.equals(that.amountOwed) : that.amountOwed != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (amountOwed != null ? amountOwed.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }
}
