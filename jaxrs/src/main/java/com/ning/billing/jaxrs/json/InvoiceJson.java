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
import org.joda.time.DateTime;

public class InvoiceJson {

    private final BigDecimal amount;
    private final String invoiceId;
    private final DateTime requestedDate;
    private final String invoiceNumber;
    private final BigDecimal balance;

    @JsonCreator
    public InvoiceJson(@JsonProperty("amount") BigDecimal amount,
            @JsonProperty("invoice_id") String invoiceId,
            @JsonProperty("requested_dt") DateTime requestedDate,
            @JsonProperty("invoice_number") String invoiceNumber,
            @JsonProperty("balance") BigDecimal balance) {
        super();
        this.amount = amount;
        this.invoiceId = invoiceId;
        this.requestedDate = requestedDate;
        this.invoiceNumber = invoiceNumber;
        this.balance = balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
