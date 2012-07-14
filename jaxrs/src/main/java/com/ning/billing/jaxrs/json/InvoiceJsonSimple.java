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

import java.math.BigDecimal;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvoiceJsonSimple {

    private final BigDecimal amount;
    private final String invoiceId;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final String invoiceNumber;
    private final BigDecimal balance;
    private final BigDecimal creditAdj;
    private final BigDecimal refundAdj;
    private final BigDecimal cba;
    private final String accountId;

    public InvoiceJsonSimple() {
        this(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, BigDecimal.ZERO, null);
    }

    @JsonCreator
    public InvoiceJsonSimple(@JsonProperty("amount") final BigDecimal amount,
                             @JsonProperty("cba") final BigDecimal cba,
                             @JsonProperty("creditAdj") final BigDecimal creditAdj,
                             @JsonProperty("refundAdj") final BigDecimal refundAdj,
                             @JsonProperty("invoiceId") @Nullable final String invoiceId,
                             @JsonProperty("invoiceDate") @Nullable final LocalDate invoiceDate,
                             @JsonProperty("targetDate") @Nullable final LocalDate targetDate,
                             @JsonProperty("invoiceNumber") @Nullable final String invoiceNumber,
                             @JsonProperty("balance") final BigDecimal balance,
                             @JsonProperty("accountId") @Nullable final String accountId) {
        this.amount = amount;
        this.cba = cba;
        this.creditAdj = creditAdj;
        this.refundAdj = refundAdj;
        this.invoiceId = invoiceId;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.invoiceNumber = invoiceNumber;
        this.balance = balance;
        this.accountId = accountId;
    }

    public InvoiceJsonSimple(final Invoice input) {
        this(input.getChargedAmount(), input.getCBAAmount(), input.getCreditAdjAmount(), input.getRefundAdjAmount(), input.getId().toString(), input.getInvoiceDate(),
             input.getTargetDate(), String.valueOf(input.getInvoiceNumber()), input.getBalance(), input.getAccountId().toString());
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getCBA() {
        return cba;
    }

    public BigDecimal getCreditAdj() {
        return creditAdj;
    }

    public BigDecimal getRefundAdj() {
        return refundAdj;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getTargetDate() {
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
        if (!((amount == null && that.amount == null) ||
              (amount != null && that.amount != null && amount.compareTo(that.amount) == 0))) {
            return false;
        }
        if (!((balance == null && that.balance == null) ||
              (balance != null && that.balance != null && balance.compareTo(that.balance) == 0))) {
            return false;
        }
        if (!((cba == null && that.cba == null) ||
              (cba != null && that.cba != null && cba.compareTo(that.cba) == 0))) {
            return false;
        }
        if (!((creditAdj == null && that.creditAdj == null) ||
              (creditAdj != null && that.creditAdj != null && creditAdj.compareTo(that.creditAdj) == 0))) {
            return false;
        }
        if (!((refundAdj == null && that.refundAdj == null) ||
              (refundAdj != null && that.refundAdj != null && refundAdj.compareTo(that.refundAdj) == 0))) {
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
        result = 31 * result + (cba != null ? cba.hashCode() : 0);
        result = 31 * result + (creditAdj != null ? creditAdj.hashCode() : 0);
        result = 31 * result + (refundAdj != null ? refundAdj.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        return result;
    }
}
