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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.calculator.InvoiceCalculatorUtils;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.InvoiceItemFactory;

public class InvoiceModelDaoHelper {

    private InvoiceModelDaoHelper() {}

    public static BigDecimal getRawBalanceForRegularInvoice(final InvoiceModelDao invoiceModelDao) {

        if (invoiceModelDao.isMigrated()) {
            return BigDecimal.ZERO;
        }

        final Iterable<InvoiceItem> invoiceItems = mapInvoiceItemModelDaoToInvoiceItem(invoiceModelDao.getInvoiceItems());

        final Iterable<InvoicePayment> invoicePayments = invoiceModelDao.getInvoicePayments().stream()
                .map(DefaultInvoicePayment::new)
                .collect(Collectors.toUnmodifiableList());

        return InvoiceCalculatorUtils.computeRawInvoiceBalance(invoiceModelDao.getCurrency(), invoiceItems, invoicePayments);
    }

    public static BigDecimal getCBAAmount(final InvoiceModelDao invoiceModelDao) {
        final Iterable<InvoiceItem> invoiceItems = mapInvoiceItemModelDaoToInvoiceItem(invoiceModelDao.getInvoiceItems());
        return InvoiceCalculatorUtils.computeInvoiceAmountCredited(invoiceModelDao.getCurrency(), invoiceItems);
    }

    public static BigDecimal getAmountCharged(final InvoiceModelDao invoiceModelDao) {
        final Iterable<InvoiceItem> invoiceItems = mapInvoiceItemModelDaoToInvoiceItem(invoiceModelDao.getInvoiceItems());
        return InvoiceCalculatorUtils.computeInvoiceAmountCharged(invoiceModelDao.getCurrency(), invoiceItems);
    }

    private static List<InvoiceItem> mapInvoiceItemModelDaoToInvoiceItem(final List<InvoiceItemModelDao> invoiceItems) {
        return invoiceItems.stream()
                .map(InvoiceItemFactory::fromModelDao)
                .collect(Collectors.toUnmodifiableList());
    }
}
