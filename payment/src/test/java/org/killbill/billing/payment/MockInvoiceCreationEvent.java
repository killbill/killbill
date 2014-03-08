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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.InvoiceCreationInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MockInvoiceCreationEvent extends BusEventBase implements InvoiceCreationInternalEvent {

    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amountOwed;
    private final Currency currency;
    private final LocalDate invoiceCreationDate;

    @JsonCreator
    public MockInvoiceCreationEvent(@JsonProperty("invoiceId") final UUID invoiceId,
                                    @JsonProperty("accountId") final UUID accountId,
                                    @JsonProperty("amountOwed") final BigDecimal amountOwed,
                                    @JsonProperty("currency") final Currency currency,
                                    @JsonProperty("invoiceCreationDate") final LocalDate invoiceCreationDate,
                                    @JsonProperty("searchKey1") final Long searchKey1,
                                    @JsonProperty("searchKey2") final Long searchKey2,
                                    @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amountOwed = amountOwed;
        this.currency = currency;
        this.invoiceCreationDate = invoiceCreationDate;
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
        return "DefaultInvoiceCreationNotification [invoiceId=" + invoiceId + ", accountId=" + accountId + ", amountOwed=" + amountOwed + ", currency=" + currency + ", invoiceCreationDate=" + invoiceCreationDate + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                 + ((amountOwed == null) ? 0 : amountOwed.hashCode());
        result = prime * result
                 + ((currency == null) ? 0 : currency.hashCode());
        result = prime
                 * result
                 + ((invoiceCreationDate == null) ? 0 : invoiceCreationDate
                .hashCode());
        result = prime * result
                 + ((invoiceId == null) ? 0 : invoiceId.hashCode());
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
        final MockInvoiceCreationEvent other = (MockInvoiceCreationEvent) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (amountOwed == null) {
            if (other.amountOwed != null) {
                return false;
            }
        } else if (!amountOwed.equals(other.amountOwed)) {
            return false;
        }
        if (currency != other.currency) {
            return false;
        }
        if (invoiceCreationDate == null) {
            if (other.invoiceCreationDate != null) {
                return false;
            }
        } else if (invoiceCreationDate.compareTo(other.invoiceCreationDate) != 0) {
            return false;
        }
        if (invoiceId == null) {
            if (other.invoiceId != null) {
                return false;
            }
        } else if (!invoiceId.equals(other.invoiceId)) {
            return false;
        }
        return true;
    }
}
