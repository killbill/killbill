/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoicePluginDispatcher;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.InvoiceItemCatalogBase;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class InvoiceApiHelper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceApiHelper.class);

    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final InvoiceDao dao;
    private final GlobalLocker locker;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceConfig invoiceConfig;

    @Inject
    public InvoiceApiHelper(final InvoicePluginDispatcher invoicePluginDispatcher, final InvoiceDao dao, final GlobalLocker locker, final InvoiceConfig invoiceConfig, final InternalCallContextFactory internalCallContextFactory) {
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.dao = dao;
        this.locker = locker;
        this.invoiceConfig = invoiceConfig;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public List<InvoiceItem> dispatchToInvoicePluginsAndInsertItems(final UUID accountId, final boolean isDryRun, final WithAccountLock withAccountLock, final Iterable<PluginProperty> properties, final CallContext context) throws InvoiceApiException {
        // Invoked by User API call
        final LocalDate targetDate = null;
        final List<Invoice> existingInvoices = null;
        final boolean isRescheduled = false;

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final DateTime rescheduleDate = invoicePluginDispatcher.priorCall(targetDate, existingInvoices, isDryRun, isRescheduled, context, properties, internalTenantContext);
        if (rescheduleDate != null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_PLUGIN_API_ABORTED, "delayed scheduling is unsupported for API calls");
        }

        boolean success = false;
        GlobalLock lock = null;
        Iterable<DefaultInvoice> invoicesForPlugins = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), invoiceConfig.getMaxGlobalLockRetries());

            invoicesForPlugins = withAccountLock.prepareInvoices();

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(accountId, context);
            final List<InvoiceModelDao> invoiceModelDaos = new LinkedList<InvoiceModelDao>();
            for (final DefaultInvoice invoiceForPlugin : invoicesForPlugins) {
                // Call plugin(s)
                invoicePluginDispatcher.updateOriginalInvoiceWithPluginInvoiceItems(invoiceForPlugin, isDryRun, context, properties, internalCallContext);

                // Transformation to InvoiceModelDao
                final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoiceForPlugin);
                final List<InvoiceItem> invoiceItems = invoiceForPlugin.getInvoiceItems();
                final List<InvoiceItemModelDao> invoiceItemModelDaos = toInvoiceItemModelDao(invoiceItems);
                invoiceModelDao.addInvoiceItems(invoiceItemModelDaos);

                // Keep track of modified invoices
                invoiceModelDaos.add(invoiceModelDao);
            }

            final List<InvoiceItemModelDao> createdInvoiceItems = dao.createInvoices(invoiceModelDaos, internalCallContext);
            success = true;

            return fromInvoiceItemModelDao(createdInvoiceItems);
        } catch (final LockFailedException e) {
            log.warn("Failed to process invoice items for accountId='{}'", accountId.toString(), e);
            return ImmutableList.<InvoiceItem>of();
        } finally {
            if (lock != null) {
                lock.release();
            }

            if (success) {
                for (final Invoice invoiceForPlugin : invoicesForPlugins) {
                    final DefaultInvoice refreshedInvoice = new DefaultInvoice(dao.getById(invoiceForPlugin.getId(), internalTenantContext));
                    invoicePluginDispatcher.onSuccessCall(targetDate, refreshedInvoice, existingInvoices, isDryRun, isRescheduled, context, properties, internalTenantContext);
                }
            } else {
                invoicePluginDispatcher.onFailureCall(targetDate, null, existingInvoices, isDryRun, isRescheduled, context, properties, internalTenantContext);
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
    public InvoiceItem createAdjustmentItem(final Invoice invoiceToBeAdjusted,
                                            final UUID invoiceItemId,
                                            @Nullable final BigDecimal positiveAdjAmount,
                                            @Nullable final Currency currency,
                                            final LocalDate effectiveDate,
                                            final String description,
                                            @Nullable final String itemDetails,
                                            final InternalCallContext context) throws InvoiceApiException {
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

        // Check the specified currency matches the one of the existing invoice
        final Currency currencyForAdjustment = MoreObjects.firstNonNull(currency, invoiceItemToBeAdjusted.getCurrency());
        if (invoiceItemToBeAdjusted.getCurrency() != currencyForAdjustment) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, currency, invoiceItemToBeAdjusted.getCurrency());
        }

        // Reuse the same logic we have for refund with item adjustment
        final Map<UUID, BigDecimal> input = new HashMap<UUID, BigDecimal>();
        input.put(invoiceItemId, positiveAdjAmount);

        final Map<UUID, BigDecimal> output = dao.computeItemAdjustments(invoiceToBeAdjusted.getId().toString(), input, context);

        // Nothing to adjust
        if (output.get(invoiceItemId) == null) {
            return null;
        }

        // If we pass that stage, it means the validation succeeded so we just need to extract resulting amount and negate the result.
        final BigDecimal amountToAdjust = output.get(invoiceItemId).negate();
        // Finally, create the adjustment

        return new InvoiceItemCatalogBase(UUIDs.randomUUID(),
                                          context.getCreatedDate(),
                                          invoiceItemToBeAdjusted.getInvoiceId(),
                                          invoiceItemToBeAdjusted.getAccountId(),
                                          null,
                                          null,
                                          description,
                                          invoiceItemToBeAdjusted.getProductName(),
                                          invoiceItemToBeAdjusted.getPlanName(),
                                          invoiceItemToBeAdjusted.getPhaseName(),
                                          invoiceItemToBeAdjusted.getUsageName(),
                                          effectiveDate,
                                          effectiveDate,
                                          amountToAdjust,
                                          null,
                                          currencyForAdjustment,
                                          invoiceItemToBeAdjusted.getId(),
                                          null,
                                          itemDetails,
                                          InvoiceItemType.ITEM_ADJ);
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
