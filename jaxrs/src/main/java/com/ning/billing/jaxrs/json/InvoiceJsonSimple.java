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

package com.ning.billing.jaxrs.json;

import javax.annotation.Nullable;
import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.invoice.api.Invoice;

public class InvoiceJsonSimple {
    private final BigDecimal amount;
    private final BigDecimal credit;
    private final String invoiceId;
    private final DateTime invoiceDate;
    private final DateTime targetDate;
    private final String invoiceNumber;
    private final BigDecimal balance;
    private final String accountId;

    public InvoiceJsonSimple() {
        this(BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, BigDecimal.ZERO, null);
    }

    @JsonCreator
    public InvoiceJsonSimple(@JsonProperty("amount") final BigDecimal amount,
                             @JsonProperty("credit") final BigDecimal credit,
                             @JsonProperty("invoiceId") @Nullable final String invoiceId,
                             @JsonProperty("invoiceDate") @Nullable final DateTime invoiceDate,
                             @JsonProperty("targetDate") @Nullable final DateTime targetDate,
                             @JsonProperty("invoiceNumber") @Nullable final String invoiceNumber,
                             @JsonProperty("balance") final BigDecimal balance,
                             @JsonProperty("accountId") @Nullable final String accountId) {
        this.amount = amount;
        this.credit = credit;
        this.invoiceId = invoiceId;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.invoiceNumber = invoiceNumber;
        this.balance = balance;
        this.accountId = accountId;
    }

    public InvoiceJsonSimple(final Invoice input) {
        this(input.getAmountCharged(), input.getAmountCredited(), input.getId().toString(), input.getInvoiceDate(),
             input.getTargetDate(), String.valueOf(input.getInvoiceNumber()), input.getBalance(), input.getAccountId().toString());
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    public DateTime getTargetDate() {
        return targetDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getAccountId() {
        return accountId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final InvoiceJsonSimple that = (InvoiceJsonSimple) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (credit != null ? !credit.equals(that.credit) : that.credit != null) {
            return false;
        }
        if (!((invoiceDate == null && that.invoiceDate == null) ||
                (invoiceDate != null && that.invoiceDate != null && invoiceDate.compareTo(that.invoiceDate) == 0))) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (!((targetDate == null && that.targetDate == null) ||
                (targetDate != null && that.targetDate != null && targetDate.compareTo(that.targetDate) == 0))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = amount != null ? amount.hashCode() : 0;
        result = 31 * result + (credit != null ? credit.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        return result;
    }
}
