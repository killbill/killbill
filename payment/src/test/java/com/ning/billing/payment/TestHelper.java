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

package com.ning.billing.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.user.AccountBuilder;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;

public class TestHelper {
    protected final AccountDao accountDao;
    protected final InvoiceDao invoiceDao;

    @Inject
    public TestHelper(AccountDao accountDao, InvoiceDao invoiceDao) {
        this.accountDao = accountDao;
        this.invoiceDao = invoiceDao;
    }

    public Account createTestAccount() throws AccountApiException {
        final String name = "First" + RandomStringUtils.random(5) + " " + "Last" + RandomStringUtils.random(5);
        final Account account = new AccountBuilder(UUID.randomUUID()).name(name)
                                                                     .firstNameLength(name.length())
                                                                     .externalKey("12345")
                                                                     .phone("123-456-7890")
                                                                     .email("user@example.com")
                                                                     .currency(Currency.USD)
                                                                     .billingCycleDay(1)
                                                                     .build();
        accountDao.create(account);
        return account;
    }

    public Invoice createTestInvoice(Account account,
                                     DateTime targetDate,
                                     Currency currency,
                                     InvoiceItem... items) {
        Invoice invoice = new DefaultInvoice(UUID.randomUUID(), account.getId(), new DateTime(), targetDate, currency);

        for (InvoiceItem item : items) {
            invoice.addInvoiceItem(new DefaultInvoiceItem(invoice.getId(),
                                               item.getSubscriptionId(),
                                               item.getPlanName(),
                                               item.getPhaseName(),
                                               item.getStartDate(),
                                               item.getEndDate(),
                                               item.getRecurringAmount(),
                                               item.getRecurringRate(),
                                               item.getFixedAmount(),
                                               item.getCurrency()));
        }
        invoiceDao.create(invoice);
        return invoice;
    }

    public Invoice createTestInvoice(Account account) {
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final UUID subscriptionId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("10.00");
        final InvoiceItem item = new DefaultInvoiceItem(null, subscriptionId, "test plan", "test phase", now, now.plusMonths(1), amount, new BigDecimal("1.0"), null, Currency.USD);

        return createTestInvoice(account, now, Currency.USD, item);
    }
}
