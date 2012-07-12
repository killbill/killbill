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
import java.util.Collection;
import java.util.List;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

public class InvoiceItemList extends ArrayList<InvoiceItem> {

    private static final long serialVersionUID = 192311667L;

    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();

    public InvoiceItemList() {
        super();
    }

    public InvoiceItemList(final List<InvoiceItem> invoiceItems) {
        super();
        this.addAll(invoiceItems);
    }

    public BigDecimal getTotalAdjAmount() {
        return getAmoutForItems(InvoiceItemType.CREDIT_ADJ, InvoiceItemType.REFUND_ADJ);
    }

    public BigDecimal getCreditAdjAmount() {
        return getAmoutForItems(InvoiceItemType.CREDIT_ADJ);
    }

    public BigDecimal getRefundAdjAmount() {
        return getAmoutForItems(InvoiceItemType.REFUND_ADJ);
    }

    public BigDecimal getChargedAmount() {
        return getAmoutForItems(InvoiceItemType.RECURRING, InvoiceItemType.FIXED, InvoiceItemType.REPAIR_ADJ);
    }

    public BigDecimal getCBAAmount() {
        return getAmoutForItems(InvoiceItemType.CBA_ADJ);
    }


    private BigDecimal getAmoutForItems(InvoiceItemType...types) {
        BigDecimal total = BigDecimal.ZERO.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        for (final InvoiceItem item : this) {
            if (isFromType(item, types)) {
                if (item.getAmount() != null) {
                    total = total.add(item.getAmount());
                }
            }
        }
        return total.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    private boolean isFromType(InvoiceItem item, InvoiceItemType...types) {
        for (InvoiceItemType cur : types) {
            if (item.getInvoiceItemType() == cur) {
                return true;
            }
        }
        return false;
    }
}
