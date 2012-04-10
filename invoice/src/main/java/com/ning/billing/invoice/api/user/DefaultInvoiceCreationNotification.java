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

package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.util.bus.BusEvent.BusEventType;

public class DefaultInvoiceCreationNotification implements InvoiceCreationNotification {
    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amountOwed;
    private final Currency currency;
    private final DateTime invoiceCreationDate;

    public DefaultInvoiceCreationNotification(UUID invoiceId, UUID accountId, BigDecimal amountOwed, Currency currency, DateTime invoiceCreationDate) {
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amountOwed = amountOwed;
        this.currency = currency;
        this.invoiceCreationDate = invoiceCreationDate;
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
    public DateTime getInvoiceCreationDate() {
        return invoiceCreationDate;
    }

    @Override
    public String toString() {
        return "DefaultInvoiceCreationNotification [invoiceId=" + invoiceId + ", accountId=" + accountId + ", amountOwed=" + amountOwed + ", currency=" + currency + ", invoiceCreationDate=" + invoiceCreationDate + "]";
    }
    
    
	@Override
	public BusEventType getBusEventType() {
		return BusEventType.INVOICE_CREATION;
	}

}
