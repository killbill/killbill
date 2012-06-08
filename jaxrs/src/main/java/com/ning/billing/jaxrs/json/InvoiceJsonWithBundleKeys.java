package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.invoice.api.Invoice;

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

public class InvoiceJsonWithBundleKeys extends InvoiceJsonSimple {
    private final String bundleKeys;

    public InvoiceJsonWithBundleKeys() {
        super();
        this.bundleKeys = null;
    }

    @JsonCreator
    public InvoiceJsonWithBundleKeys(@JsonProperty("amount") final BigDecimal amount,
                                     @JsonProperty("credit") final BigDecimal credit,
                                     @JsonProperty("invoiceId") final String invoiceId,
                                     @JsonProperty("invoiceDate") final DateTime invoiceDate,
                                     @JsonProperty("targetDate") final DateTime targetDate,
                                     @JsonProperty("invoiceNumber") final String invoiceNumber,
                                     @JsonProperty("balance") final BigDecimal balance,
                                     @JsonProperty("accountId") final String accountId,
                                     @JsonProperty("externalBundleKeys") final String bundleKeys) {
        super(amount, credit, invoiceId, invoiceDate, targetDate, invoiceNumber, balance, accountId);
        this.bundleKeys = bundleKeys;
    }

    public InvoiceJsonWithBundleKeys(final Invoice input, final String bundleKeys) {
        super(input);
        this.bundleKeys = bundleKeys;
    }

    public String getBundleKeys() {
        return bundleKeys;
    }
}
