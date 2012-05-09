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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;
import org.joda.time.DateTime;

import com.ning.billing.invoice.api.Invoice;

public class InvoiceJson {

    @JsonView(BundleTimelineViews.Base.class)
    private final BigDecimal amount;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final String invoiceId;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final DateTime invoiceDate;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final String invoiceNumber;
    
    @JsonView(BundleTimelineViews.Base.class)
    private final BigDecimal balance;

    
    public InvoiceJson() {
        this.amount = null;
        this.invoiceId = null;
        this.invoiceDate = null;
        this.invoiceNumber = null;
        this.balance = null;
    }
    
    @JsonCreator
    public InvoiceJson(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("invoice_id") String invoiceId,
            @JsonProperty("invoice_date") DateTime invoiceDate,
            @JsonProperty("invoice_number") String invoiceNumber,
            @JsonProperty("balance") BigDecimal balance) {
        super();
        this.amount = amount;
        this.invoiceId = invoiceId;
        this.invoiceDate = invoiceDate;
        this.invoiceNumber = invoiceNumber;
        this.balance = balance;
    }

    public InvoiceJson(Invoice input) {
        this.amount = input.getTotalAmount();
        this.invoiceId = input.getId().toString();
        this.invoiceDate = input.getInvoiceDate();
        this.invoiceNumber = String.valueOf(input.getInvoiceNumber());
        this.balance = input.getBalance();
    }
    
    public BigDecimal getAmount() {
        return amount;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((amount == null) ? 0 : amount.hashCode());
        result = prime * result + ((balance == null) ? 0 : balance.hashCode());
        result = prime * result
                + ((invoiceDate == null) ? 0 : invoiceDate.hashCode());
        result = prime * result
                + ((invoiceId == null) ? 0 : invoiceId.hashCode());
        result = prime * result
                + ((invoiceNumber == null) ? 0 : invoiceNumber.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InvoiceJson other = (InvoiceJson) obj;
        if (amount == null) {
            if (other.amount != null)
                return false;
        } else if (!amount.equals(other.amount))
            return false;
        if (balance == null) {
            if (other.balance != null)
                return false;
        } else if (!balance.equals(other.balance))
            return false;
        if (invoiceDate == null) {
            if (other.invoiceDate != null)
                return false;
        } else if (!invoiceDate.equals(other.invoiceDate))
            return false;
        if (invoiceId == null) {
            if (other.invoiceId != null)
                return false;
        } else if (!invoiceId.equals(other.invoiceId))
            return false;
        if (invoiceNumber == null) {
            if (other.invoiceNumber != null)
                return false;
        } else if (!invoiceNumber.equals(other.invoiceNumber))
            return false;
        return true;
    }
}
