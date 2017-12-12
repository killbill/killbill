/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.proRations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.TestInvoiceHelper;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.clock.Clock;
import org.mockito.Mockito;
import org.testng.Assert;

import com.google.common.collect.ImmutableList;

public class InvoiceTestUtils {

    private InvoiceTestUtils() {}

    public static Invoice createAndPersistInvoice(final TestInvoiceHelper testInvoiceHelper,
                                                  final InvoiceDao invoiceDao,
                                                  final Clock clock,
                                                  final BigDecimal amount,
                                                  final Currency currency,
                                                  final InternalCallContext internalCallContext) {
        try {
            return createAndPersistInvoice(testInvoiceHelper,
                                           invoiceDao,
                                           clock,
                                           ImmutableList.<BigDecimal>of(amount),
                                           currency,
                                           internalCallContext);
        } catch (final EntityPersistenceException e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    public static Invoice createAndPersistInvoice(final TestInvoiceHelper testInvoiceHelper,
                                                  final InvoiceDao invoiceDao,
                                                  final Clock clock,
                                                  final Iterable<BigDecimal> amounts,
                                                  final Currency currency,
                                                  final InternalCallContext internalCallContext) throws EntityPersistenceException {
        final Invoice invoice = Mockito.mock(Invoice.class);
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId;
        try {
            final Account account = testInvoiceHelper.createAccount(internalCallContext.toCallContext(null));
            accountId = account.getId();
        } catch (final AccountApiException e) {
            Assert.fail(e.getMessage());
            return null;
        }

        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getTargetDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getCurrency()).thenReturn(currency);
        Mockito.when(invoice.isMigrationInvoice()).thenReturn(false);
        Mockito.when(invoice.getStatus()).thenReturn(InvoiceStatus.COMMITTED);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final List<InvoiceItemModelDao> invoiceModelItems = new ArrayList<InvoiceItemModelDao>();
        for (final BigDecimal amount : amounts) {
            final InvoiceItem invoiceItem = createInvoiceItem(clock, invoiceId, accountId, amount, currency);
            invoiceModelItems.add(new InvoiceItemModelDao(invoiceItem));
            invoiceItems.add(invoiceItem);
        }
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);

        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        invoiceModelDao.addInvoiceItems(invoiceModelItems);
        invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(invoiceModelDao), internalCallContext);

        return invoice;
    }

    public static InvoiceItem createInvoiceItem(final Clock clock, final UUID invoiceId, final UUID accountId, final BigDecimal amount, final Currency currency) {
        return new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(), UUID.randomUUID(),
                                         "charge back test", "charge back phase", clock.getUTCToday(), amount, currency);
    }

    public static InvoicePayment createAndPersistPayment(final InvoiceInternalApi invoicePaymentApi,
                                                         final Clock clock,
                                                         final UUID invoiceId,
                                                         final BigDecimal amount,
                                                         final Currency currency,
                                                         final InternalCallContext callContext) throws InvoiceApiException {
        final InvoicePayment payment = Mockito.mock(InvoicePayment.class);
        Mockito.when(payment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        Mockito.when(payment.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(payment.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentCookieId()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(payment.getPaymentDate()).thenReturn(clock.getUTCNow());
        Mockito.when(payment.getAmount()).thenReturn(amount);
        Mockito.when(payment.getCurrency()).thenReturn(currency);
        Mockito.when(payment.getProcessedCurrency()).thenReturn(currency);
        Mockito.when(payment.isSuccess()).thenReturn(true);

        invoicePaymentApi.recordPaymentAttemptCompletion(payment.getInvoiceId(), payment.getAmount(), payment.getCurrency(), payment.getProcessedCurrency(), payment.getPaymentId(), payment.getPaymentCookieId(),
                                                         payment.getPaymentDate(), payment.isSuccess(), callContext);

        return payment;
    }
}
