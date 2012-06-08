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

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.invoice.api.Invoice;

public class InvoiceJsonSimple {
    private final BigDecimal amount;
    private final BigDecimal credit;
    private final String     invoiceId;
    private final DateTime   invoiceDate;
    private final DateTime   targetDate;
    private final String     invoiceNumber;
    private final BigDecimal balance;
    private final String     accountId;

    public InvoiceJsonSimple() {
        this.amount = BigDecimal.ZERO;
        this.credit = BigDecimal.ZERO;
        this.invoiceId = null;
        this.invoiceDate = null;
        this.targetDate = null;
        this.invoiceNumber = null;
        this.balance = BigDecimal.ZERO;
        this.accountId = null;
    }

    @JsonCreator
    public InvoiceJsonSimple(@JsonProperty("amount") final BigDecimal amount,
                             @JsonProperty("credit") final BigDecimal credit,
                             @JsonProperty("invoiceId") final String invoiceId,
                             @JsonProperty("invoiceDate") final DateTime invoiceDate,
                             @JsonProperty("targetDate") final DateTime targetDate,
                             @JsonProperty("invoiceNumber") final String invoiceNumber,
                             @JsonProperty("balance") final BigDecimal balance,
                             @JsonProperty("accountId") final String accountId) {
        super();
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
        this.amount = input.getAmountCharged();
        this.credit = input.getAmountCredited();
        this.invoiceId = input.getId().toString();
        this.invoiceDate = input.getInvoiceDate();
        this.targetDate = input.getTargetDate();
        this.invoiceNumber = String.valueOf(input.getInvoiceNumber());
        this.balance = input.getBalance();
        this.accountId = input.getAccountId().toString();
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((amount == null) ? 0 : amount.hashCode());
        result = prime * result + ((balance == null) ? 0 : balance.hashCode());
        result = prime * result + ((credit == null) ? 0 : credit.hashCode());
        result = prime * result
                + ((invoiceDate == null) ? 0 : invoiceDate.hashCode());
        result = prime * result
                + ((invoiceId == null) ? 0 : invoiceId.hashCode());
        result = prime * result
                + ((invoiceNumber == null) ? 0 : invoiceNumber.hashCode());
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
        final InvoiceJsonSimple other = (InvoiceJsonSimple) obj;
        if (amount == null) {
            if (other.amount != null) {
                return false;
            }
        } else if (!amount.equals(other.amount)) {
            return false;
        }

        if (balance == null) {
            if (other.balance != null) {
                return false;
            }
        } else if (!balance.equals(other.balance)) {
            return false;
        }

        if (credit == null) {
            if (other.credit != null) {
                return false;
            }
        } else if (!credit.equals(other.credit)) {
            return false;
        }

        if (invoiceDate == null) {
            if (other.invoiceDate != null) {
                return false;
            }
        } else if (!invoiceDate.equals(other.invoiceDate)) {
            return false;
        }

        if (invoiceId == null) {
            if (other.invoiceId != null) {
                return false;
            }
        } else if (!invoiceId.equals(other.invoiceId)) {
            return false;
        }

        if (invoiceNumber == null) {
            if (other.invoiceNumber != null) {
                return false;
            }
        } else if (!invoiceNumber.equals(other.invoiceNumber)) {
            return false;
        }

        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }

        return true;
    }
}
