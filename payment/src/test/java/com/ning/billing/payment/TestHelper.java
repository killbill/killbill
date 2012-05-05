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
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.entity.EntityPersistenceException;

public class TestHelper {
    protected final AccountUserApi accountUserApi;
    protected final InvoicePaymentApi invoicePaymentApi;
    private final CallContext context;
    private final Bus eventBus;

    @Inject
    public TestHelper(CallContextFactory factory, AccountUserApi accountUserApi, InvoicePaymentApi invoicePaymentApi, Bus eventBus) {
        this.eventBus = eventBus;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        context = factory.createCallContext("Princess Buttercup", CallOrigin.TEST, UserType.TEST);
    }

    // These helper methods can be overridden in a plugin implementation
    public Account createTestCreditCardAccount() throws EntityPersistenceException {
        final String name = "First" + RandomStringUtils.randomAlphanumeric(5) + " " + "Last" + RandomStringUtils.randomAlphanumeric(5);
        final String externalKey = RandomStringUtils.randomAlphanumeric(10);
        final Account account = new MockAccountBuilder(UUID.randomUUID()).name(name)
                                                                     .firstNameLength(name.length())
                                                                     .externalKey(externalKey)
                                                                     .phone("123-456-7890")
                                                                     .email("ccuser" + RandomStringUtils.randomAlphanumeric(8) + "@example.com")
                                                                     .currency(Currency.USD)
                                                                     .billingCycleDay(1)
                                                                     .build();
        ((ZombieControl)accountUserApi).addResult("getAccountById", account);
        ((ZombieControl)accountUserApi).addResult("getAccountByKey", account);
        return account;
    }

    public Account createTestPayPalAccount() throws EntityPersistenceException {
        final String name = "First" + RandomStringUtils.randomAlphanumeric(5) + " " + "Last" + RandomStringUtils.randomAlphanumeric(5);
        final String externalKey = RandomStringUtils.randomAlphanumeric(10);
        final Account account = new MockAccountBuilder(UUID.randomUUID()).name(name)
                                                                     .firstNameLength(name.length())
                                                                     .externalKey(externalKey)
                                                                     .phone("123-456-7890")
                                                                     .email("ppuser@example.com")
                                                                     .currency(Currency.USD)
                                                                     .billingCycleDay(1)
                                                                     .build();
        ((ZombieControl)accountUserApi).addResult("getAccountById", account);
        ((ZombieControl)accountUserApi).addResult("getAccountByKey", account);
        return account;
    }

    public Invoice createTestInvoice(Account account,
                                     DateTime targetDate,
                                     Currency currency,
                                     InvoiceItem... items) throws EventBusException {
        Invoice invoice = new MockInvoice(account.getId(), new DateTime(), targetDate, currency);

        for (InvoiceItem item : items) {
            if (item instanceof MockRecurringInvoiceItem) {
                MockRecurringInvoiceItem recurringInvoiceItem = (MockRecurringInvoiceItem) item;
                invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                               account.getId(),
                                                               recurringInvoiceItem.getBundleId(),
                                                               recurringInvoiceItem.getSubscriptionId(),
                                                               recurringInvoiceItem.getPlanName(),
                                                               recurringInvoiceItem.getPhaseName(),
                                                               recurringInvoiceItem.getStartDate(),
                                                               recurringInvoiceItem.getEndDate(),
                                                               recurringInvoiceItem.getAmount(),
                                                               recurringInvoiceItem.getRate(),
                                                               recurringInvoiceItem.getCurrency()));
            }
        }

 //       invoiceTestApi.create(invoice, context);
        ((ZombieControl)invoicePaymentApi).addResult("getInvoice", invoice);
        InvoiceCreationEvent event = new MockInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                invoice.getBalance(), invoice.getCurrency(),
                invoice.getInvoiceDate(),
                context.getUserToken());
        
        eventBus.post(event);
        return invoice;
    }

    public Invoice createTestInvoice(Account account) throws EventBusException {
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("10.00");
        
        final InvoiceItem item = new MockRecurringInvoiceItem(null, account.getId(), bundleId, subscriptionId, "test plan", "test phase", now, now.plusMonths(1),
                amount, new BigDecimal("1.0"), Currency.USD);


        return createTestInvoice(account, now, Currency.USD, item);
    }
}
