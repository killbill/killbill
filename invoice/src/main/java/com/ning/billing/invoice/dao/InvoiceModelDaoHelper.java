/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;

import com.ning.billing.invoice.model.InvoiceItemList;

public class InvoiceModelDaoHelper {

    private InvoiceModelDaoHelper() {}

    public static BigDecimal getBalance(final InvoiceModelDao invoiceModelDao) {
        final InvoiceItemList invoiceItems = new InvoiceItemList(invoiceModelDao.getInvoiceItems());
        return invoiceItems.getBalance(getPaidAmount(invoiceModelDao));
    }

    public static BigDecimal getCBAAmount(final InvoiceModelDao invoiceModelDao) {
        final InvoiceItemList invoiceItems = new InvoiceItemList(invoiceModelDao.getInvoiceItems());
        return invoiceItems.getCBAAmount();
    }

    public static BigDecimal getPaidAmount(final InvoiceModelDao invoiceModelDao) {
        // Compute payments
        BigDecimal amountPaid = BigDecimal.ZERO;
        for (final InvoicePaymentModelDao payment : invoiceModelDao.getInvoicePayments()) {
            if (payment.getAmount() != null) {
                amountPaid = amountPaid.add(payment.getAmount());
            }
        }

        return amountPaid;
    }

    public static BigDecimal getChargedAmount(final InvoiceModelDao invoiceModelDao) {
        final InvoiceItemList invoiceItems = new InvoiceItemList(invoiceModelDao.getInvoiceItems());
        return invoiceItems.getChargedAmount();
    }
}
