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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.dao.InvoiceItemModelDao;

public class InvoiceItemFactory {

    private InvoiceItemFactory() {}

    public static InvoiceItem fromModelDao(final InvoiceItemModelDao invoiceItemModelDao) {
        if (invoiceItemModelDao == null) {
            return null;
        }

        final UUID id = invoiceItemModelDao.getId();
        final DateTime createdDate = invoiceItemModelDao.getCreatedDate();
        final UUID invoiceId = invoiceItemModelDao.getInvoiceId();
        final UUID accountId = invoiceItemModelDao.getAccountId();
        final UUID bundleId = invoiceItemModelDao.getBundleId();
        final UUID subscriptionId = invoiceItemModelDao.getSubscriptionId();
        final String planName = invoiceItemModelDao.getPlanName();
        final String phaseName = invoiceItemModelDao.getPhaseName();
        final LocalDate startDate = invoiceItemModelDao.getStartDate();
        final LocalDate endDate = invoiceItemModelDao.getEndDate();
        final BigDecimal amount = invoiceItemModelDao.getAmount();
        final BigDecimal rate = invoiceItemModelDao.getRate();
        final Currency currency = invoiceItemModelDao.getCurrency();
        final UUID linkedItemId = invoiceItemModelDao.getLinkedItemId();

        final InvoiceItem item;
        final InvoiceItemType type = invoiceItemModelDao.getType();
        switch (type) {
            case EXTERNAL_CHARGE:
                item = new ExternalChargeInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, planName, startDate, amount, currency);
                break;
            case FIXED:
                item = new FixedPriceInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
                break;
            case RECURRING:
                item = new RecurringInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
                break;
            case CBA_ADJ:
                item = new CreditBalanceAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, linkedItemId, amount, currency);
                break;
            case CREDIT_ADJ:
                item = new CreditAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency);
                break;
            case REFUND_ADJ:
                item = new RefundAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency);
                break;
            case REPAIR_ADJ:
                item = new RepairAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, endDate, amount, currency, linkedItemId);
                break;
            case ITEM_ADJ:
                item = new ItemAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency, linkedItemId);
                break;
            default:
                throw new RuntimeException("Unexpected type of event item " + type);
        }

        return item;
    }
}
