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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ning.billing.invoice.api.InvoiceItem;

public class InvoiceItemList extends ArrayList<InvoiceItem> {
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMethod();
    public InvoiceItemList() {
        super();
    }

    public InvoiceItemList(final List<InvoiceItem> invoiceItems) {
        super();
        this.addAll(invoiceItems);
    }

    public BigDecimal getTotalAmount() {
        // TODO: Jeff -- naive implementation, assumes all invoice items share the same currency
        BigDecimal total = new BigDecimal("0");

        for (final InvoiceItem item : this) {
            total = total.add(item.getAmount());
        }

        return total.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public void removeCancellingPairs() {
        List<InvoiceItem> itemsToRemove = new ArrayList<InvoiceItem>();

        for (int firstItemIndex = 0; firstItemIndex < this.size(); firstItemIndex++) {
            for (int secondItemIndex = firstItemIndex + 1; secondItemIndex < this.size(); secondItemIndex++) {
                InvoiceItem firstItem = this.get(firstItemIndex);
                InvoiceItem secondItem = this.get(secondItemIndex);
                if (firstItem.cancels(secondItem)) {
                    itemsToRemove.add(firstItem);
                    itemsToRemove.add(secondItem);
                }
            }
        }

        this.removeAll(itemsToRemove);
    }
}
