/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoicePluginDispatcher;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class InvoiceApiHelper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceApiHelper.class);

    private static final int NB_LOCK_TRY = 5;

    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final InvoiceDao dao;
    private final GlobalLocker locker;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceApiHelper(final InvoicePluginDispatcher invoicePluginDispatcher, final InvoiceDao dao, final GlobalLocker locker, final InternalCallContextFactory internalCallContextFactory) {
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.dao = dao;
        this.locker = locker;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public List<InvoiceItem> dispatchToInvoicePluginsAndInsertItems(final UUID accountId, final WithAccountLock withAccountLock, final CallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), accountId.toString(), NB_LOCK_TRY);

            final Iterable<Invoice> invoicesForPlugins = withAccountLock.prepareInvoices();

            final List<InvoiceModelDao> invoiceModelDaos = new LinkedList<InvoiceModelDao>();
            for (final Invoice invoiceForPlugin : invoicesForPlugins) {
                // Call plugin
                final List<InvoiceItem> additionalInvoiceItems = invoicePluginDispatcher.getAdditionalInvoiceItems(invoiceForPlugin, context);
                invoiceForPlugin.addInvoiceItems(additionalInvoiceItems);

                // Transformation to InvoiceModelDao
                final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoiceForPlugin);
                final List<InvoiceItem> invoiceItems = invoiceForPlugin.getInvoiceItems();
                final List<InvoiceItemModelDao> invoiceItemModelDaos = toInvoiceItemModelDao(invoiceItems);
                invoiceModelDao.addInvoiceItems(invoiceItemModelDaos);

                // Keep track of modified invoices
                invoiceModelDaos.add(invoiceModelDao);
            }

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(accountId, context);
            final List<InvoiceItemModelDao> createdInvoiceItems = dao.createInvoices(invoiceModelDaos, internalCallContext);
            return fromInvoiceItemModelDao(createdInvoiceItems);
        } catch (final LockFailedException e) {
            log.error(String.format("Failed to process invoice items for account %s", accountId.toString()), e);
            return ImmutableList.<InvoiceItem>of();
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    /**
     * Create an adjustment for a given invoice item. This just creates the object in memory, it doesn't write it to disk.
     *
     * @param invoiceToBeAdjusted the invoice
     * @param invoiceItemId       the invoice item id to adjust
     * @param positiveAdjAmount   the amount to adjust. Pass null to adjust the full amount of the original item
     * @param currency            the currency of the amount. Pass null to default to the original currency used
     * @param effectiveDate       adjustment effective date, in the account timezone
     * @return the adjustment item
     */
    public InvoiceItem createAdjustmentItem(final Invoice invoiceToBeAdjusted, final UUID invoiceItemId,
                                            @Nullable final BigDecimal positiveAdjAmount, @Nullable final Currency currency,
                                            final LocalDate effectiveDate, final InternalCallContext context) throws InvoiceApiException {
        final InvoiceItem invoiceItemToBeAdjusted = Iterables.<InvoiceItem>tryFind(invoiceToBeAdjusted.getInvoiceItems(),
                                                                                   new Predicate<InvoiceItem>() {
                                                                                       @Override
                                                                                       public boolean apply(final InvoiceItem input) {
                                                                                           return input.getId().equals(invoiceItemId);
                                                                                       }
                                                                                   }).orNull();
        if (invoiceItemToBeAdjusted == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
        }

        // Retrieve the amount and currency if needed
        final BigDecimal amountToAdjust = Objects.firstNonNull(positiveAdjAmount, invoiceItemToBeAdjusted.getAmount());
        // Check the specified currency matches the one of the existing invoice
        final Currency currencyForAdjustment = Objects.firstNonNull(currency, invoiceItemToBeAdjusted.getCurrency());
        if (invoiceItemToBeAdjusted.getCurrency() != currencyForAdjustment) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, currency, invoiceItemToBeAdjusted.getCurrency());
        }

        // Finally, create the adjustment
        return new ItemAdjInvoiceItem(UUID.randomUUID(),
                                      context.getCreatedDate(),
                                      invoiceItemToBeAdjusted.getInvoiceId(),
                                      invoiceItemToBeAdjusted.getAccountId(),
                                      effectiveDate,
                                      null,
                                      // Note! The amount is negated here!
                                      amountToAdjust.negate(),
                                      currencyForAdjustment,
                                      invoiceItemToBeAdjusted.getId());
    }

    private List<InvoiceItem> fromInvoiceItemModelDao(final Collection<InvoiceItemModelDao> invoiceItemModelDaos) {
        return ImmutableList.<InvoiceItem>copyOf(Collections2.transform(invoiceItemModelDaos,
                                                                        new Function<InvoiceItemModelDao, InvoiceItem>() {
                                                                            @Override
                                                                            public InvoiceItem apply(final InvoiceItemModelDao input) {
                                                                                return InvoiceItemFactory.fromModelDao(input);
                                                                            }
                                                                        }));
    }

    private List<InvoiceItemModelDao> toInvoiceItemModelDao(final Collection<InvoiceItem> invoiceItems) {
        return ImmutableList.copyOf(Collections2.transform(invoiceItems,
                                                           new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                               @Override
                                                               public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                   return new InvoiceItemModelDao(input);
                                                               }
                                                           }));
    }
}
