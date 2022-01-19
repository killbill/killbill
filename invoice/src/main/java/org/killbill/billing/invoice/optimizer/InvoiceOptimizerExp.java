/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.invoice.optimizer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class InvoiceOptimizerExp extends InvoiceOptimizerBase {

    private static Logger logger = LoggerFactory.getLogger(InvoiceOptimizerExp.class);

    private static Period UNSPECIFIED_PERIOD = new Period(InvoiceConfig.DEFAULT_NULL_PERIOD);

    @Inject
    public InvoiceOptimizerExp(final InvoiceDao invoiceDao,
                               final Clock clock,
                               final InvoiceConfig invoiceConfig) {
        super(invoiceDao, clock, invoiceConfig);
        logger.info("Feature InvoiceOptimizer is ON");
    }

    @Override
    public AccountInvoices getInvoices(final InternalCallContext callContext) {
        final Period maxInvoiceLimit = invoiceConfig.getMaxInvoiceLimit(callContext);

        boolean isMaxInvoiceLimitSet = maxInvoiceLimit != null && !maxInvoiceLimit.equals(UNSPECIFIED_PERIOD);

        final LocalDate cutoffDt = isMaxInvoiceLimitSet ? callContext.toLocalDate(clock.getUTCNow()).minus(maxInvoiceLimit) : null;
        //
        // We need to compute a 'cutoffDt' for junction (billing events) that is at least one period less than the one computed for invoice
        // to support in-arrear trailing pro-ration use cases - i.e cancellation did not occur EOT.
        // The strategy is to use the existing config to remove one more period than what has been specified for invoice.
        // Note that it's ok to return more but returning not enough would lead to unexpected REPAIR
        // (See TestWithInvoiceOptimization#testRecurringInArrear5 for instance)
        //
        final LocalDate beCutoffDt = isMaxInvoiceLimitSet ? cutoffDt.minus(maxInvoiceLimit) : null;
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final List<InvoiceModelDao> invoicesByAccount = invoiceDao.getInvoicesByAccount(false, cutoffDt, null, callContext);
        for (final InvoiceModelDao invoiceModelDao : invoicesByAccount) {
            existingInvoices.add(new DefaultInvoice(invoiceModelDao));
        }
        return new AccountInvoicesExp(cutoffDt, beCutoffDt, existingInvoices);
    }

    @Override
    public boolean rescheduleProcessAccount(final UUID accountId, final InternalCallContext context) {
        // Anything below 1sec, we would ignore
        final TimeSpan timeSpan = invoiceConfig.getRescheduleIntervalOnLock(context);
        final int delaySec = (int) TimeUnit.SECONDS.convert(timeSpan.getMillis(), TimeUnit.MILLISECONDS);
        if (delaySec <= 0) {
            return false;
        }
        final DateTime nextRescheduleDt = clock.getUTCNow().plusSeconds(delaySec);
        logger.info("Rescheduling invoice call at time {}", nextRescheduleDt);
        invoiceDao.rescheduleInvoiceNotification(accountId, nextRescheduleDt, context);
        return true;
    }

    public static class AccountInvoicesExp extends AccountInvoices {

        public AccountInvoicesExp(final LocalDate cutoffDate, final LocalDate beCutoffDate, final List<Invoice> invoices) {
            super(cutoffDate, beCutoffDate, invoices);
        }

        public AccountInvoicesExp() {
            super();
        }

        @Override
        public void filterProposedItems(final List<InvoiceItem> proposedItems, final BillingEventSet eventSet, final InternalCallContext internalCallContext) {
            if (cutoffDate != null) {

                // Assumption: A given Plan#BillingMode and PlanPhase#BillingPeriod remains constant across catalog version
                // TODO Catalog validation to ensure this

                // Comes from the Plan
                final Map<String, BillingMode> billingModes = new HashMap<>();
                // Comes from the PlanPhase
                final Map<String, BillingPeriod> billingPeriods = new HashMap<>();
                final Iterable<InvoiceItem> filtered = Iterables.filter(proposedItems, new Predicate<InvoiceItem>() {
                    @Override
                    public boolean apply(final InvoiceItem invoiceItem) {
                        if (invoiceItem.getInvoiceItemType() == InvoiceItemType.FIXED) {
                            return invoiceItem.getStartDate().compareTo(cutoffDate) >= 0;
                        }
                        Preconditions.checkState(invoiceItem.getInvoiceItemType() == InvoiceItemType.RECURRING, "Expected (proposed) item %s to be a RECURRING invoice item", invoiceItem);

                        // Extract Plan info associated with item by correlating with list of billing events
                        // From plan info, retrieve billing mode.
                        BillingMode billingMode = billingModes.get(invoiceItem.getPlanName());
                        BillingPeriod billingPeriod = billingPeriods.get(invoiceItem.getPhaseName());
                        if (billingMode == null || billingPeriod == null) {
                            // Best effort logic to find the correct billing event ('be'):
                            // We could simplify and look for any 'be' whose Plan matches the one from the invoiceItem,
                            // but in unlikely scenarios where there are multiple Plans across catalog versions with different BillingMode,
                            // we could end up with the wrong billing event (and therefore billing mode). Therefore, the complexity.
                            // (all this because catalog is not available in this layer)
                            //
                            final Iterator<BillingEvent> it = ((NavigableSet<BillingEvent>) eventSet).descendingIterator();
                            while (it.hasNext()) {
                                final BillingEvent be = it.next();
                                if (!be.getSubscriptionId().equals(invoiceItem.getSubscriptionId()) /* wrong subscription ID */ ||
                                        /* Not the correct plan */
                                    !(be.getPlan() != null && be.getPlan().getName().equals(invoiceItem.getPlanName())) ||
                                        /* Whether in-advance or in-arrear (what we are trying to find out), the 'be' we want is the one where ii.endDate >= be.effDt */
                                    invoiceItem.getEndDate().compareTo(internalCallContext.toLocalDate(be.getEffectiveDate())) < 0) {
                                    continue;
                                }
                                billingMode = be.getPlan().getRecurringBillingMode();
                                billingModes.put(invoiceItem.getPlanName(), billingMode);

                                billingPeriod = be.getPlanPhase().getRecurring().getBillingPeriod();
                                billingPeriods.put(invoiceItem.getPhaseName(), billingPeriod);
                                break;
                            }
                        }

                        if ((billingMode == BillingMode.IN_ADVANCE && invoiceItem.getStartDate().compareTo(cutoffDate) >= 0) ||
                            (billingMode == BillingMode.IN_ARREAR && invoiceItem.getEndDate().compareTo(cutoffDate) >= 0)) {
                            return true;
                        } else {
                            for (final Invoice inv : invoices) {
                                final InvoiceItem existingItem = Iterables.tryFind(inv.getInvoiceItems(), new Predicate<InvoiceItem>() {
                                    @Override
                                    public boolean apply(final InvoiceItem item) {
                                        // If we find a similar item in the 'existing' list, i.e same subscription, same start date,
                                        // we keep it so it cancels out in the tree later.
                                        // We don't include the end date to catch trailing pro-ration (early cancellation)
                                        //
                                        return (item.getInvoiceItemType() == InvoiceItemType.RECURRING &&
                                                item.getSubscriptionId().equals(invoiceItem.getSubscriptionId()) &&
                                                item.getStartDate().compareTo(invoiceItem.getStartDate()) == 0);
                                    }
                                }).orNull();
                                if (existingItem != null) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    }
                });
                final List<InvoiceItem> filteredProposed = ImmutableList.copyOf(filtered);
                proposedItems.clear();
                proposedItems.addAll(filteredProposed);
            }
        }
    }
}
