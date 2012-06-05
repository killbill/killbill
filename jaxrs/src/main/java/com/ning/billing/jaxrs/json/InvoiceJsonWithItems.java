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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

public class InvoiceJsonWithItems extends InvoiceJsonSimple {
    private final List<InvoiceItemJsonSimple> items;

    @JsonCreator
    public InvoiceJsonWithItems(@JsonProperty("amount") BigDecimal amount,
                                @JsonProperty("credit") BigDecimal credit,
                                @JsonProperty("invoiceId") String invoiceId,
                                @JsonProperty("invoiceDate") DateTime invoiceDate,
                                @JsonProperty("targetDate") DateTime targetDate,
                                @JsonProperty("invoiceNumber") String invoiceNumber,
                                @JsonProperty("balance") BigDecimal balance,
                                @JsonProperty("accountId") String accountId,
            @JsonProperty("items") List<InvoiceItemJsonSimple> items) {
        super(amount, credit, invoiceId, invoiceDate, targetDate, invoiceNumber, balance, accountId);
        this.items = new ArrayList<InvoiceItemJsonSimple>(items);
    }

    public InvoiceJsonWithItems(Invoice input) {
        super(input);
        this.items = new ArrayList<InvoiceItemJsonSimple>(input.getInvoiceItems().size());
        for (InvoiceItem item : input.getInvoiceItems()) {
            this.items.add(new InvoiceItemJsonSimple(item));
        }
    }

    public List<InvoiceItemJsonSimple> getItems() {
        return Collections.unmodifiableList(items);
    }
}
