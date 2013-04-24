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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvoiceJsonWithItems extends InvoiceJsonSimple {

    private final List<InvoiceItemJsonSimple> items;

    @JsonCreator
    public InvoiceJsonWithItems(@JsonProperty("amount") final BigDecimal amount,
                                @JsonProperty("creditAdj") final BigDecimal creditAdj,
                                @JsonProperty("refundAdj") final BigDecimal refundAdj,
                                @JsonProperty("invoiceId") final String invoiceId,
                                @JsonProperty("invoiceDate") final LocalDate invoiceDate,
                                @JsonProperty("targetDate") final LocalDate targetDate,
                                @JsonProperty("invoiceNumber") final String invoiceNumber,
                                @JsonProperty("balance") final BigDecimal balance,
                                @JsonProperty("accountId") final String accountId,
                                @JsonProperty("items") final List<InvoiceItemJsonSimple> items,
                                @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(amount, creditAdj, refundAdj, invoiceId, invoiceDate, targetDate, invoiceNumber, balance, accountId, auditLogs);
        this.items = new ArrayList<InvoiceItemJsonSimple>(items);
    }

    public InvoiceJsonWithItems(final Invoice input) {
        super(input);
        this.items = new ArrayList<InvoiceItemJsonSimple>(input.getInvoiceItems().size());
        for (final InvoiceItem item : input.getInvoiceItems()) {
            this.items.add(new InvoiceItemJsonSimple(item));
        }
    }

    public List<InvoiceItemJsonSimple> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final InvoiceJsonWithItems that = (InvoiceJsonWithItems) o;

        if (items != null ? !items.equals(that.items) : that.items != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (items != null ? items.hashCode() : 0);
        return result;
    }
}
