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

package com.ning.billing.invoice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.invoice.api.user.DefaultNullInvoiceEvent;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceDateUtils;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BillingEventSet;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerType;
import com.ning.billing.util.globallocker.LockFailedException;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class InvoiceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);
    private static final int NB_LOCK_TRY = 5;

    private final InvoiceGenerator generator;
    private final BillingApi billingApi;
    private final AccountUserApi accountUserApi;
    private final InvoiceDao invoiceDao;
    private final InvoiceNotifier invoiceNotifier;
    private final GlobalLocker locker;
    private final Bus eventBus;
    private final Clock clock;

    private final boolean VERBOSE_OUTPUT;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator, final AccountUserApi accountUserApi,
                             final BillingApi billingApi,
                             final InvoiceDao invoiceDao,
                             final InvoiceNotifier invoiceNotifier,
                             final GlobalLocker locker,
                             final Bus eventBus,
                             final Clock clock) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.accountUserApi = accountUserApi;
        this.invoiceDao = invoiceDao;
        this.invoiceNotifier = invoiceNotifier;
        this.locker = locker;
        this.eventBus = eventBus;
        this.clock = clock;

        final String verboseOutputValue = System.getProperty("VERBOSE_OUTPUT");
        VERBOSE_OUTPUT = (verboseOutputValue != null) && Boolean.parseBoolean(verboseOutputValue);
    }

    public void processSubscription(final EffectiveSubscriptionEvent transition,
                                    final CallContext context) throws InvoiceApiException {
        final UUID subscriptionId = transition.getSubscriptionId();
        final DateTime targetDate = transition.getEffectiveTransitionTime();
        log.info("Got subscription transition: type: " + transition.getTransitionType().toString() + "; id: " + subscriptionId.toString() + "; targetDate: " + targetDate.toString());
        processSubscription(subscriptionId, targetDate, context);
    }

    public void processSubscription(final UUID subscriptionId, final DateTime targetDate,
                                    final CallContext context) throws InvoiceApiException {
        try {
            if (subscriptionId == null) {
                log.error("Failed handling entitlement change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
                return;
            }
            final UUID accountId = billingApi.getAccountIdFromSubscriptionId(subscriptionId);
            processAccount(accountId, targetDate, false, context);
        } catch (EntitlementBillingApiException e) {
            log.error("Failed handling entitlement change.",
                      new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
        }
    }

    public Invoice processAccount(final UUID accountId, final DateTime targetDate,
                                  final boolean dryRun, final CallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT, accountId.toString(), NB_LOCK_TRY);

            return processAccountWithLock(accountId, targetDate, dryRun, context);
        } catch (LockFailedException e) {
            // Not good!
            log.error(String.format("Failed to process invoice for account %s, targetDate %s",
                                    accountId.toString(), targetDate), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private Invoice processAccountWithLock(final UUID accountId, final DateTime targetDateTime,
                                           final boolean dryRun, final CallContext context) throws InvoiceApiException {
        try {
            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId);
            final Account account = accountUserApi.getAccountById(accountId);

            List<Invoice> invoices = new ArrayList<Invoice>();
            if (!billingEvents.isAccountAutoInvoiceOff()) {
                invoices = invoiceDao.getInvoicesByAccount(accountId); //no need to fetch, invoicing is off on this account
            }

            final Currency targetCurrency = account.getCurrency();

            // All the computations in invoice are performed on days, in the account timezone
            final LocalDate targetDate = new LocalDate(targetDateTime, account.getTimeZone());

            final Invoice invoice = generator.generateInvoice(accountId, billingEvents, invoices, targetDate, account.getTimeZone(), targetCurrency);

            if (invoice == null) {
                log.info("Generated null invoice.");
                outputDebugData(billingEvents, invoices);
                if (!dryRun) {
                    final BusEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(), context.getUserToken());
                    postEvent(event, accountId);
                }
            } else {
                log.info("Generated invoice {} with {} items.", invoice.getId().toString(), invoice.getNumberOfItems());
                if (VERBOSE_OUTPUT) {
                    log.info("New items");
                    for (final InvoiceItem item : invoice.getInvoiceItems()) {
                        log.info(item.toString());
                    }
                }
                outputDebugData(billingEvents, invoices);
                if (!dryRun) {

                    // We need to check whether this is just a 'shell' invoice or a real invoice with items on it
                    final boolean isRealInvoiceWithItems = Collections2.filter(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
                        @Override
                        public boolean apply(InvoiceItem input) {
                            return input.getInvoiceId().equals(invoice.getId());
                        }
                    }).size() > 0;

                    invoiceDao.create(invoice, account.getBillCycleDay().getDayOfMonthUTC(), isRealInvoiceWithItems, context);

                    final List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
                    final List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
                    setChargedThroughDates(account.getBillCycleDay(), fixedPriceInvoiceItems, recurringInvoiceItems, context);

                    final InvoiceCreationEvent event = new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                                       invoice.getBalance(), invoice.getCurrency(),
                                                                                       context.getUserToken());

                    postEvent(event, accountId);
                }
            }

            if (account.isNotifiedForInvoices()) {
                invoiceNotifier.notify(account, invoice);
            }

            return invoice;
        } catch (AccountApiException e) {
            log.error("Failed handling entitlement change.", e);
            return null;
        }
    }

    private void setChargedThroughDates(final BillCycleDay billCycleDay,
                                        final Collection<InvoiceItem> fixedPriceItems,
                                        final Collection<InvoiceItem> recurringItems,
                                        final CallContext context) {
        // TODO - this should be handled by entitlement
        final Map<UUID, DateTime> chargeThroughDates = new HashMap<UUID, DateTime>();
        addInvoiceItemsToChargeThroughDates(billCycleDay, chargeThroughDates, fixedPriceItems);
        addInvoiceItemsToChargeThroughDates(billCycleDay, chargeThroughDates, recurringItems);

        for (final UUID subscriptionId : chargeThroughDates.keySet()) {
            if (subscriptionId != null) {
                final DateTime chargeThroughDate = chargeThroughDates.get(subscriptionId);
                log.info("Setting CTD for subscription {} to {}", subscriptionId.toString(), chargeThroughDate.toString());
                billingApi.setChargedThroughDate(subscriptionId, chargeThroughDate, context);
            }
        }
    }

    private void postEvent(final BusEvent event, final UUID accountId) {
        try {
            eventBus.post(event);
        } catch (EventBusException e) {
            log.error(String.format("Failed to post event %s for account %s", event.getBusEventType(), accountId), e);
        }
    }

    private void addInvoiceItemsToChargeThroughDates(final BillCycleDay billCycleDay,
                                                     final Map<UUID, DateTime> chargeThroughDates,
                                                     final Collection<InvoiceItem> items) {
        for (final InvoiceItem item : items) {
            final UUID subscriptionId = item.getSubscriptionId();
            final DateTime endDate;
            if (item.getEndDate() != null) {
                endDate = new DateTime(item.getEndDate().toDateTime(clock.getUTCNow()) , DateTimeZone.UTC);
            } else {
                // item end date is null for fixed price items for instance
                endDate = new DateTime(item.getStartDate().toDateTime(clock.getUTCNow()), DateTimeZone.UTC);
            }

            if (chargeThroughDates.containsKey(subscriptionId)) {
                if (chargeThroughDates.get(subscriptionId).isBefore(endDate)) {
                    // The CTD should always align with the BCD
                    final DateTime ctd = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(endDate, billCycleDay.getDayOfMonthLocal());
                    chargeThroughDates.put(subscriptionId, ctd);
                }
            } else {
                chargeThroughDates.put(subscriptionId, endDate);
            }
        }
    }

    private void outputDebugData(final Collection<BillingEvent> events, final Collection<Invoice> invoices) {
        if (VERBOSE_OUTPUT) {
            log.info("Events");
            for (final BillingEvent event : events) {
                log.info(event.toString());
            }

            log.info("Existing items");
            for (final Invoice invoice : invoices) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    log.info(item.toString());
                }
            }
        }
    }
}
