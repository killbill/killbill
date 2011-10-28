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

package com.ning.billing.invoice.model;

import com.ning.billing.catalog.api.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Invoice {
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    private final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
    private Currency currency;

    public Invoice() {}

    public Invoice(Currency currency) {
        this.currency = currency;
    }

    public boolean add(InvoiceItem item) {
        return items.add(item);
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        // TODO: Jeff -- naive implementation, assumes all invoice items share the same currency
        BigDecimal total = new BigDecimal("0");

        for (InvoiceItem item : items) {
            total = total.add(item.getAmount());
        }

        return total.setScale(NUMBER_OF_DECIMALS);
    }

    public int getNumberOfItems() {
        return items.size();
    }
}

