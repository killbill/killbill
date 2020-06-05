/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.RequestedSubscriptionInternalEvent;
import org.killbill.billing.events.SubscriptionInternalEvent;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.FutureAccountNotificationsBuilder;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.user.DefaultInvoiceNotificationInternalEvent;
import org.killbill.billing.invoice.api.user.DefaultNullInvoiceEvent;
import org.killbill.billing.invoice.calculator.InvoiceCalculatorUtils;
import org.killbill.billing.invoice.dao.ExistingInvoiceMetadata;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDaoHelper;
import org.killbill.billing.invoice.dao.InvoiceParentChildModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates.UsageDef;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.ParentInvoiceItem;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.notification.NextBillingDateNotificationKey;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class InvoiceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);

    private static final Ordering<DateTime> UPCOMING_NOTIFICATION_DATE_ORDERING = Ordering.natural();
    private static final Joiner JOINER_COMMA = Joiner.on(",");
    private static final TargetDateDryRunArguments TARGET_DATE_DRY_RUN_ARGUMENTS = new TargetDateDryRunArguments();

    private final InvoiceGenerator generator;
    private final BillingInternalApi billingApi;
    private final AccountInternalApi accountApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceDao invoiceDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final GlobalLocker locker;
    private final PersistentBus eventBus;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final InvoiceConfig invoiceConfig;
    private final ParkedAccountsManager parkedAccountsManager;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator,
                             final AccountInternalApi accountApi,
                             final BillingInternalApi billingApi,
                             final SubscriptionBaseInternalApi SubscriptionApi,
                             final InvoiceDao invoiceDao,
                             final InternalCallContextFactory internalCallContextFactory,
                             final InvoicePluginDispatcher invoicePluginDispatcher,
                             final GlobalLocker locker,
                             final PersistentBus eventBus,
                             final NotificationQueueService notificationQueueService,
                             final InvoiceConfig invoiceConfig,
                             final Clock clock,
                             final ParkedAccountsManager parkedAccountsManager) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.subscriptionApi = SubscriptionApi;
        this.accountApi = accountApi;
        this.invoiceDao = invoiceDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.locker = locker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.invoiceConfig = invoiceConfig;
        this.parkedAccountsManager = parkedAccountsManager;
    }

    public void processSubscriptionStartRequestedDate(final RequestedSubscriptionInternalEvent transition, final InternalCallContext context) {
        final long dryRunNotificationTime = invoiceConfig.getDryRunNotificationSchedule(context).getMillis();
        final boolean isInvoiceNotificationEnabled = dryRunNotificationTime > 0;
        if (!isInvoiceNotificationEnabled) {
            return;
        }

        final UUID accountId;
        try {
            accountId = subscriptionApi.getAccountIdFromSubscriptionId(transition.getSubscriptionId(), context);
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed handling SubscriptionBase change.",
                     new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, transition.getSubscriptionId().toString()));
            return;
        }

        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), invoiceConfig.getMaxGlobalLockRetries());

            processSubscriptionStartRequestedDateWithLock(accountId, transition, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process RequestedSubscriptionInternalEvent for accountId='{}'", accountId.toString(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    private void processSubscriptionStartRequestedDateWithLock(final UUID accountId, final RequestedSubscriptionInternalEvent transition, final InternalCallContext context) {
        try {
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, null, context);
            if (billingEvents.isEmpty()) {
                return;
            }

            final FutureAccountNotificationsBuilder notificationsBuilder = new FutureAccountNotificationsBuilder();
            populateNextFutureDryRunNotificationDate(billingEvents, notificationsBuilder, context);

            final ImmutableAccountData account = accountApi.getImmutableAccountDataById(accountId, context);

            setFutureNotifications(account, notificationsBuilder.build(), context);
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed handling SubscriptionBase change.",
                     new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, transition.getSubscriptionId().toString()));
        } catch (final AccountApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}'", accountId, e);
        } catch (final CatalogApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}'", accountId, e);
        }
    }

    public void processSubscriptionForInvoiceGeneration(final SubscriptionInternalEvent transition,
                                                        final InternalCallContext context) throws InvoiceApiException {
        final UUID subscriptionId = transition.getSubscriptionId();
        final LocalDate targetDate = context.toLocalDate(transition.getEffectiveTransitionTime());
        processSubscriptionForInvoiceGeneration(subscriptionId, targetDate, false, context);
    }

    public void processSubscriptionForInvoiceGeneration(final UUID subscriptionId, final LocalDate targetDate, final boolean isRescheduled, final InternalCallContext context) throws InvoiceApiException {
        processSubscriptionInternal(subscriptionId, targetDate, false, isRescheduled, context);
    }

    public void processSubscriptionForInvoiceNotification(final UUID subscriptionId,
                                                          final LocalDate targetDate,
                                                          final InternalCallContext context) throws InvoiceApiException {
        final Iterable<Invoice> dryRunInvoices = processSubscriptionInternal(subscriptionId, targetDate, true, false, context);
        if (dryRunInvoices == null) {
            return;
        }

        for (final Invoice dryRunInvoice : dryRunInvoices) {
            if (dryRunInvoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            final BusEvent event = new DefaultInvoiceNotificationInternalEvent(dryRunInvoice.getAccountId(),
                                                                               dryRunInvoice.getBalance(),
                                                                               dryRunInvoice.getCurrency(),
                                                                               context.toUTCDateTime(targetDate),
                                                                               context.getAccountRecordId(),
                                                                               context.getTenantRecordId(),
                                                                               context.getUserToken());
            try {
                eventBus.post(event);
            } catch (final EventBusException e) {
                log.warn("Failed to post event {}", event, e);
            }
        }
    }

    private Iterable<Invoice> processSubscriptionInternal(final UUID subscriptionId,
                                                          final LocalDate targetDate,
                                                          final boolean dryRunForNotification,
                                                          final boolean isRescheduled,
                                                          final InternalCallContext context) throws InvoiceApiException {
        try {
            if (subscriptionId == null) {
                log.warn("Failed handling SubscriptionBase change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
                return null;
            }
            final UUID accountId = subscriptionApi.getAccountIdFromSubscriptionId(subscriptionId, context);
            final DryRunArguments dryRunArguments = dryRunForNotification ? TARGET_DATE_DRY_RUN_ARGUMENTS : null;

            return processAccountFromNotificationOrBusEvent(accountId, targetDate, dryRunArguments, isRescheduled, context);
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed handling SubscriptionBase change.",
                     new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return null;
        }
    }

    public Iterable<Invoice> processAccountFromNotificationOrBusEvent(final UUID accountId,
                                                                      @Nullable final LocalDate targetDate,
                                                                      @Nullable final DryRunArguments dryRunArguments,
                                                                      final boolean isRescheduled,
                                                                      final InternalCallContext context) throws InvoiceApiException {
        if (!invoiceConfig.isInvoicingSystemEnabled(context)) {
            log.warn("Invoicing system is off, parking accountId='{}'", accountId);
            parkAccount(accountId, context);
            return null;
        }

        return processAccount(false, accountId, targetDate, dryRunArguments, isRescheduled, context);
    }

    public Iterable<Invoice> processAccount(final boolean isApiCall,
                                            final UUID accountId,
                                            @Nullable final LocalDate targetDate,
                                            @Nullable final DryRunArguments dryRunArguments,
                                            final boolean isRescheduled,
                                            final InternalCallContext context) throws InvoiceApiException {
        boolean parkedAccount = false;
        try {
            parkedAccount = parkedAccountsManager.isParked(context);
            if (parkedAccount && !isApiCall) {
                log.warn("Ignoring invoice generation process for accountId='{}', targetDate='{}', account is parked", accountId.toString(), targetDate);
                return null;
            }
        } catch (final TagApiException e) {
            log.warn("Unable to determine parking state for accountId='{}'", accountId);
        }

        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), invoiceConfig.getMaxGlobalLockRetries());

            return processAccountWithLock(parkedAccount, accountId, targetDate, dryRunArguments, isRescheduled, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process invoice for accountId='{}', targetDate='{}'", accountId.toString(), targetDate, e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private Iterable<Invoice> processAccountWithLock(final boolean parkedAccount,
                                                     final UUID accountId,
                                                     @Nullable final LocalDate inputTargetDateMaybeNull,
                                                     @Nullable final DryRunArguments dryRunArguments,
                                                     final boolean isRescheduled,
                                                     final InternalCallContext context) throws InvoiceApiException {
        final boolean isDryRun = dryRunArguments != null;
        final boolean upcomingInvoiceDryRun = isDryRun && DryRunType.UPCOMING_INVOICE.equals(dryRunArguments.getDryRunType());

        LocalDate inputTargetDate = inputTargetDateMaybeNull;
        // A null inputTargetDate is only allowed in UPCOMING_INVOICE dryRun mode to have the system compute it
        if (inputTargetDate == null && !upcomingInvoiceDryRun) {
            inputTargetDate = context.toLocalDate(clock.getUTCNow());
        }
        Preconditions.checkArgument(inputTargetDate != null || upcomingInvoiceDryRun, "inputTargetDate is required in non dryRun mode");

        try {
            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, dryRunArguments, context);
            if (billingEvents.isAccountAutoInvoiceOff()) {
                return null;
            }

            // Avoid pulling all invoices when AUTO_INVOICING_OFF is set since we will disable invoicing later
            // (Note that we can't return right away as we send a NullInvoice event)
            final List<Invoice> existingInvoices = billingEvents.isAccountAutoInvoiceOff() ?
                                                   ImmutableList.<Invoice>of() :
                                                   ImmutableList.<Invoice>copyOf(Collections2.transform(invoiceDao.getInvoicesByAccount(false, context),
                                                                                                        new Function<InvoiceModelDao, Invoice>() {
                                                                                                            @Override
                                                                                                            public Invoice apply(final InvoiceModelDao input) {
                                                                                                                return new DefaultInvoice(input);
                                                                                                            }
                                                                                                        }));
            final Iterable<Invoice> invoices;
            if (!isDryRun) {
                final InvoicesWithFutureNotifications invoicesWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, inputTargetDate, billingEvents, existingInvoices, false, isRescheduled, context);
                invoices = invoicesWithFutureNotifications != null ? invoicesWithFutureNotifications.getInvoices() : null;
                if (parkedAccount) {
                    try {
                        log.info("Illegal invoicing state fixed for accountId='{}', unparking account", accountId);
                        parkedAccountsManager.unparkAccount(accountId, context);
                    } catch (final TagApiException ignored) {
                        log.warn("Unable to unpark account", ignored);
                    }
                }
            } else /* Dry run use cases */ {
                final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(KILLBILL_SERVICES.INVOICE_SERVICE.getServiceName(),
                                                                                                          DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
                final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotificationsIterable = notificationQueue.getFutureNotificationForSearchKeys(context.getAccountRecordId(), context.getTenantRecordId());
                // Copy the results as retrieving the iterator will issue a query each time. This also makes sure the underlying JDBC connection is closed.
                final List<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = ImmutableList.<NotificationEventWithMetadata<NextBillingDateNotificationKey>>copyOf(futureNotificationsIterable);

                final Map<UUID, DateTime> nextScheduledSubscriptionsEventMap = getNextTransitionsForSubscriptions(billingEvents);

                // List of all existing invoice notifications
                final Set<LocalDate> allCandidateTargetDates = getUpcomingInvoiceCandidateDates(futureNotifications, nextScheduledSubscriptionsEventMap, ImmutableList.<UUID>of(), context);

                if (dryRunArguments.getDryRunType() == DryRunType.UPCOMING_INVOICE) {

                    final Iterable<UUID> filteredSubscriptionIdsForDryRun = getFilteredSubscriptionIdsFor_UPCOMING_INVOICE_DryRun(dryRunArguments, billingEvents);

                    // List of existing invoice notifications associated to the filter set of subscriptionIds
                    final Set<LocalDate> filteredCandidateTargetDates = Iterables.isEmpty(filteredSubscriptionIdsForDryRun) ?
                                                                        allCandidateTargetDates :
                                                                        getUpcomingInvoiceCandidateDates(futureNotifications, nextScheduledSubscriptionsEventMap, filteredSubscriptionIdsForDryRun, context);

                    if (Iterables.isEmpty(filteredSubscriptionIdsForDryRun)) {
                        invoices = processDryRun_UPCOMING_INVOICE_Invoice(accountId, allCandidateTargetDates, billingEvents, existingInvoices, context);
                    } else {
                        invoices = processDryRun_UPCOMING_INVOICE_FILTERING_Invoice(accountId, filteredCandidateTargetDates, allCandidateTargetDates, billingEvents, existingInvoices, context);
                    }
                } else /* DryRunType.TARGET_DATE, SUBSCRIPTION_ACTION */ {
                    invoices = processDryRun_TARGET_DATE_Invoice(accountId, inputTargetDate, allCandidateTargetDates, billingEvents, existingInvoices, context);
                }
            }
            return invoices;
        } catch (final CatalogApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        } catch (final AccountApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        } catch (final InvoiceApiException e) {
            if (e.getCode() == ErrorCode.INVOICE_PLUGIN_API_ABORTED.getCode()) {
                return null;
            }

            if (e.getCode() == ErrorCode.UNEXPECTED_ERROR.getCode() && !isDryRun) {
                log.warn("Illegal invoicing state detected for accountId='{}', dryRunArguments='{}', parking account", accountId, dryRunArguments, e);
                parkAccount(accountId, context);
            }
            throw e;
        } catch (final NoSuchNotificationQueue e) {
            // Should not happen, notificationQ is only used for dry run mode
            if (!isDryRun) {
                log.warn("Missing notification queue, accountId='{}', dryRunArguments='{}', parking account", accountId, dryRunArguments, e);
                parkAccount(accountId, context);
            }
            throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR, "Failed to retrieve future notifications from notificationQ");
        }
    }

    // Return a map of subscriptionId / localDate identifying what is the next upcoming billing transition (PHASE, PAUSE, ..)
    private Map<UUID, DateTime> getNextTransitionsForSubscriptions(final BillingEventSet billingEvents) {

        final DateTime now = clock.getUTCNow();
        final Map<UUID, DateTime> result = new HashMap<UUID, DateTime>();
        for (final BillingEvent evt : billingEvents) {
            final UUID subscriptionId = evt.getSubscriptionId();
            final DateTime evtEffectiveDate = evt.getEffectiveDate();
            if (evtEffectiveDate.compareTo(now) <= 0) {
                continue;
            }
            final DateTime nextUpcomingPerSubscriptionDate = result.get(subscriptionId);
            if (nextUpcomingPerSubscriptionDate == null || nextUpcomingPerSubscriptionDate.compareTo(evtEffectiveDate) > 0) {
                result.put(subscriptionId, evtEffectiveDate);
            }
        }
        return result;
    }

    private Iterable<Invoice> processDryRun_UPCOMING_INVOICE_Invoice(final UUID accountId,
                                                                     final Iterable<LocalDate> allCandidateTargetDates,
                                                                     final BillingEventSet billingEvents,
                                                                     final List<Invoice> existingInvoices,
                                                                     final InternalCallContext context) throws InvoiceApiException {
        for (final LocalDate curTargetDate : allCandidateTargetDates) {
            final InvoicesWithFutureNotifications invoicesWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, curTargetDate, billingEvents, existingInvoices, true, false, context);
            final Iterable<Invoice> invoices = invoicesWithFutureNotifications != null ? invoicesWithFutureNotifications.getInvoices() : null;
            if (invoices != null) {
                return invoices;
            }
        }
        return null;
    }

    private Iterable<Invoice> processDryRun_UPCOMING_INVOICE_FILTERING_Invoice(final UUID accountId,
                                                                               final Iterable<LocalDate> filteringCandidateTargetDates,
                                                                               final Set<LocalDate> allCandidateTargetDates,
                                                                               final BillingEventSet billingEvents,
                                                                               final List<Invoice> existingInvoices,
                                                                               final InternalCallContext context) throws InvoiceApiException {
        for (final LocalDate curTargetDate : filteringCandidateTargetDates) {
            final Iterable<Invoice> invoices = processDryRun_TARGET_DATE_Invoice(accountId, curTargetDate, allCandidateTargetDates, billingEvents, existingInvoices, context);
            if (invoices != null) {
                return invoices;
            }
        }
        return null;
    }

    private Iterable<Invoice> processDryRun_TARGET_DATE_Invoice(final UUID accountId,
                                                                final LocalDate targetDate,
                                                                final Set<LocalDate> allCandidateTargetDates,
                                                                final BillingEventSet billingEvents,
                                                                final List<Invoice> existingInvoices,
                                                                final InternalCallContext context) throws InvoiceApiException {
        final PriorityQueue<LocalDate> pq = new PriorityQueue<LocalDate>(allCandidateTargetDates);

        // Keeps track of generated invoices as we go through the list
        // The list is an ordered list of items merged from existing notifications and upcoming notifications, each of these the result of a previous invoice being generated.
        final List<Invoice> augmentedExistingInvoices = new ArrayList<Invoice>(existingInvoices);
        Iterable<Invoice> additionalInvoices = null;
        LocalDate cur;
        while ((cur = pq.poll()) != null) {
            if (cur.compareTo(targetDate) >= 0) {
                break;
            }
            // Loop through each boundary date prior to our given targetDate
            final InvoicesWithFutureNotifications result = processAccountWithLockAndInputTargetDate(accountId, cur, billingEvents, augmentedExistingInvoices, true, false, context);
            additionalInvoices = result != null ? result.getInvoices() : null;
            if (additionalInvoices != null) {
                for (final LocalDate k : result.getNotifications().getNotificationsForTrigger().keySet()) {
                    if (k.compareTo(cur) > 0 && k.compareTo(targetDate) < 0) {
                        pq.add(k);
                    }
                }
                Iterables.addAll(augmentedExistingInvoices, additionalInvoices);
            }
        }

        final InvoicesWithFutureNotifications invoicesWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, targetDate, billingEvents, augmentedExistingInvoices, true, false, context);
        if (invoicesWithFutureNotifications == null || invoicesWithFutureNotifications.getInvoices() == null) {
            return additionalInvoices;
        } else {
            return invoicesWithFutureNotifications.getInvoices();
        }
    }

    private void parkAccount(final UUID accountId, final InternalCallContext context) {
        try {
            parkedAccountsManager.parkAccount(accountId, context);
        } catch (final TagApiException ignored) {
            log.warn("Unable to park account", ignored);
        }
    }

    private Iterable<UUID> getFilteredSubscriptionIdsFor_UPCOMING_INVOICE_DryRun(@Nullable final DryRunArguments dryRunArguments, final BillingEventSet billingEvents) {
        if (dryRunArguments == null ||
            !dryRunArguments.getDryRunType().equals(DryRunType.UPCOMING_INVOICE) ||
            (dryRunArguments.getSubscriptionId() == null && dryRunArguments.getBundleId() == null)) {
            return ImmutableList.<UUID>of();
        }

        if (dryRunArguments.getSubscriptionId() != null) {
            return ImmutableList.of(dryRunArguments.getSubscriptionId());
        }

        return Iterables.transform(Iterables.filter(billingEvents, new Predicate<BillingEvent>() {
            @Override
            public boolean apply(final BillingEvent input) {
                return input.getBundleId().equals(dryRunArguments.getBundleId());
            }
        }), new Function<BillingEvent, UUID>() {
            @Override
            public UUID apply(final BillingEvent input) {
                return input.getSubscriptionId();
            }
        });
    }

    private InvoicesWithFutureNotifications processAccountWithLockAndInputTargetDate(final UUID accountId,
                                                                                     final LocalDate originalTargetDate,
                                                                                     final BillingEventSet billingEvents,
                                                                                     final List<Invoice> existingInvoices,
                                                                                     final boolean isDryRun,
                                                                                     final boolean isRescheduled,
                                                                                     final InternalCallContext internalCallContext) throws InvoiceApiException {
        final CallContext callContext = buildCallContext(internalCallContext);

        final ImmutableAccountData account;
        try {
            account = accountApi.getImmutableAccountDataById(accountId, internalCallContext);
        } catch (final AccountApiException e) {
            log.error("Unable to generate invoice for accountId='{}', a future notification has NOT been recorded", accountId, e);
            invoicePluginDispatcher.onFailureCall(originalTargetDate, null, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
            return null;
        }

        final DateTime rescheduleDate = invoicePluginDispatcher.priorCall(originalTargetDate, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
        if (rescheduleDate != null) {
            if (isDryRun) {
                log.warn("Ignoring rescheduleDate='{}', delayed scheduling is unsupported in dry-run", rescheduleDate);
            } else {
                final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(rescheduleDate, billingEvents, internalCallContext);
                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
            }
            return null;
        }

        final InvoiceWithMetadata invoiceWithMetadata = generateKillBillInvoice(account, originalTargetDate, billingEvents, existingInvoices, internalCallContext);
        final DefaultInvoice originalInvoice = invoiceWithMetadata.getInvoice();

        // Compute future notifications
        final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(invoiceWithMetadata, billingEvents, internalCallContext);

        // If invoice comes back null, there is nothing new to generate, we can bail early
        if (originalInvoice == null) {
            invoicePluginDispatcher.onSuccessCall(originalTargetDate, null, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);

            if (isDryRun) {
                log.info("Generated null dryRun invoice for accountId='{}', targetDate='{}'", accountId, originalTargetDate);
            } else {
                log.info("Generated null invoice for accountId='{}', targetDate='{}'", accountId, originalTargetDate);

                final BusInternalEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(),
                                                                           internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(), internalCallContext.getUserToken());

                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
                postEvent(event);
            }
            return null;
        }

        final LocalDate actualTargetDate = originalInvoice.getTargetDate();
        final List<DefaultInvoice> generatedInvoices = new LinkedList<DefaultInvoice>();
        boolean success = false;
        // Final list to be returned
        final List<DefaultInvoice> reHydratedInvoices = new LinkedList<DefaultInvoice>();
        try {
            // Generate missing credit (> 0 for generation and < 0 for use) prior we call the plugin(s)
            final InvoiceItem cbaItemPreInvoicePlugins = computeCBAOnExistingInvoice(originalInvoice, internalCallContext);
            if (cbaItemPreInvoicePlugins != null) {
                originalInvoice.addInvoiceItem(cbaItemPreInvoicePlugins);
            }

            //
            // Ask external invoice plugins if additional items (tax, etc) shall be added to the invoice
            //
            final List<DefaultInvoice> updatedInvoices = invoicePluginDispatcher.updateOriginalInvoiceWithPluginInvoiceItems(originalInvoice, isDryRun, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
            if (updatedInvoices == null) {
                // Optimization
                generatedInvoices.add(originalInvoice);
            } else {
                generatedInvoices.addAll(updatedInvoices);
            }

            // Remove the temporary CBA item, unless it has been updated by the plugin
            if (cbaItemPreInvoicePlugins != null) {
                // We need to go through all invoices, in case the plugin decided to move that item around
                invoiceDispatcher:
                for (final Iterator<DefaultInvoice> invoiceIterator = generatedInvoices.iterator(); invoiceIterator.hasNext(); ) {
                    final DefaultInvoice generatedInvoice = invoiceIterator.next();
                    for (final Iterator<InvoiceItem> invoiceItemIterator = generatedInvoice.getInvoiceItems().iterator(); invoiceItemIterator.hasNext(); ) {
                        final InvoiceItem generatedInvoiceItem = invoiceItemIterator.next();
                        if (cbaItemPreInvoicePlugins.getId().equals(generatedInvoiceItem.getId()) &&
                            // The plugin can override the description of the CBA item
                            cbaItemPreInvoicePlugins.getDescription().equals(generatedInvoiceItem.getDescription())) {
                            invoiceItemIterator.remove();
                            if (generatedInvoice.getInvoiceItems().isEmpty()) {
                                invoiceIterator.remove();
                            }
                            break invoiceDispatcher;
                        }
                    }
                }
            }

            // For non dry-run scenarii, the CBA will be recomputed in the DAO layer (https://github.com/killbill/killbill/issues/637)
            if (isDryRun) {
                for (final DefaultInvoice generatedInvoice : generatedInvoices) {
                    final InvoiceItem cbaItemPostInvoicePlugins = computeCBAOnExistingInvoice(generatedInvoice, internalCallContext);
                    if (cbaItemPostInvoicePlugins != null) {
                        generatedInvoice.addInvoiceItem(cbaItemPostInvoicePlugins);
                    }
                }
                success = true;
            } else {
                final List<InvoiceModelDao> invoiceModelDaos = new LinkedList<InvoiceModelDao>();
                for (final DefaultInvoice generatedInvoice : generatedInvoices) {
                    if (generatedInvoice.getInvoiceItems().isEmpty()) {
                        continue;
                    }
                    logInvoiceWithItems(account, generatedInvoice, actualTargetDate);

                    // Transformation to Invoice -> InvoiceModelDao
                    final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(generatedInvoice);
                    final List<InvoiceItemModelDao> invoiceItemModelDaos = transformToInvoiceModelDao(generatedInvoice.getInvoiceItems());
                    invoiceModelDao.addInvoiceItems(invoiceItemModelDaos);
                    invoiceModelDaos.add(invoiceModelDao);
                }

                if (invoiceModelDaos.isEmpty()) {
                    setFutureNotifications(account, futureAccountNotifications, internalCallContext);
                } else {
                    // Note: we're demultiplexing the tracking ids here (same tracking ids for all invoices generated), as we don't
                    // really know how the plugin(s) did the grouping. If multiple invoice items were generated for a a given subscription
                    // for instance (e.g. catch-up scenario), they might now end up on different invoices.
                    final Set<InvoiceTrackingModelDao> trackingIds = new HashSet<InvoiceTrackingModelDao>();
                    for (final TrackingRecordId cur : invoiceWithMetadata.getTrackingIds()) {
                        for (final DefaultInvoice generatedInvoice : generatedInvoices) {
                            trackingIds.add(new InvoiceTrackingModelDao(cur.getTrackingId(),
                                                                        generatedInvoice.getId(),
                                                                        cur.getSubscriptionId(),
                                                                        cur.getUnitType(),
                                                                        cur.getRecordDate()));
                        }
                    }

                    // Commit invoice on disk
                    final ExistingInvoiceMetadata existingInvoiceMetadata = new ExistingInvoiceMetadata(existingInvoices);
                    commitInvoiceAndSetFutureNotifications(invoiceModelDaos, billingEvents, trackingIds, futureAccountNotifications, existingInvoiceMetadata, internalCallContext);
                    success = true;
                }

                for (final DefaultInvoice generatedInvoice : generatedInvoices) {
                    // See https://github.com/killbill/killbill/issues/1296
                    if (generatedInvoice.getStatus() == InvoiceStatus.COMMITTED) {
                        try {
                            setChargedThroughDates(generatedInvoice, internalCallContext);
                        } catch (final SubscriptionBaseApiException e) {
                            log.error("Failed handling SubscriptionBase change.", e);
                            success = false;
                        }
                    }
                }
            }
        } finally {
            // Make sure we always set future notifications in case of errors
            if (!isDryRun && !success) {
                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
            }

            if (isDryRun) {
                // In dry-run mode, if an invoice plugin item adjusts an existing item, we technically need to pull the full invoice back
                // and merge it with the generated items. This hasn't been implemented yet as the API above doesn't support returning a list
                // of invoices. There is one buggy corner case today where a dry-run would generate a unique invoice item adjustment on an existing invoice:
                // in this case, the API would only return the newly generated item adjustment, and not the full invoice.
                reHydratedInvoices.addAll(generatedInvoices);
            } else {
                for (final DefaultInvoice generatedInvoice : generatedInvoices) {
                    reHydratedInvoices.add(new DefaultInvoice(invoiceDao.getById(generatedInvoice.getId(), internalCallContext)));
                }
            }

            if (success) {
                for (final DefaultInvoice reHydratedInvoice : reHydratedInvoices) {
                    if (isDryRun) {
                        invoicePluginDispatcher.onSuccessCall(actualTargetDate, reHydratedInvoice, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
                    } else {
                        invoicePluginDispatcher.onSuccessCall(actualTargetDate, reHydratedInvoice, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
                    }
                }
            } else {
                invoicePluginDispatcher.onFailureCall(actualTargetDate, originalInvoice, existingInvoices, isDryRun, isRescheduled, callContext, ImmutableList.<PluginProperty>of(), internalCallContext);
            }
        }

        return new InvoicesWithFutureNotifications(reHydratedInvoices, futureAccountNotifications);
    }

    private InvoiceWithMetadata generateKillBillInvoice(final ImmutableAccountData account,
                                                        final LocalDate targetDate,
                                                        final BillingEventSet billingEvents,
                                                        final Iterable<Invoice> existingInvoices,
                                                        final InternalCallContext context) throws InvoiceApiException {
        final UUID targetInvoiceId;
        // Filter out DRAFT invoices for computation  of existing items unless Account is in AUTO_INVOICING_REUSE_DRAFT
        if (billingEvents.isAccountAutoInvoiceReuseDraft()) {
            final Invoice existingDraft = Iterables.tryFind(existingInvoices, new Predicate<Invoice>() {
                @Override
                public boolean apply(final Invoice input) {
                    return input.getStatus() == InvoiceStatus.DRAFT;
                }
            }).orNull();
            targetInvoiceId = existingDraft != null ? existingDraft.getId() : null;
        } else {
            targetInvoiceId = null;
        }

        return generator.generateInvoice(account, billingEvents, existingInvoices, targetInvoiceId, targetDate, account.getCurrency(), context);
    }

    private FutureAccountNotifications createNextFutureNotificationDate(final DateTime rescheduleDate, final BillingEventSet billingEvents, final InternalCallContext context) {
        final FutureAccountNotificationsBuilder notificationsBuilder = new FutureAccountNotificationsBuilder();
        notificationsBuilder.setRescheduled(true);

        final Set<UUID> subscriptionIds = ImmutableSet.<UUID>copyOf(Iterables.<BillingEvent, UUID>transform(billingEvents,
                                                                                                            new Function<BillingEvent, UUID>() {
                                                                                                                @Override
                                                                                                                public UUID apply(final BillingEvent billingEvent) {
                                                                                                                    return billingEvent.getSubscriptionId();
                                                                                                                }
                                                                                                            }));
        populateNextFutureNotificationDate(rescheduleDate, subscriptionIds, notificationsBuilder, context);

        // Even though a plugin forced us to reschedule the invoice generation, honor the dry run notifications settings
        populateNextFutureDryRunNotificationDate(billingEvents, notificationsBuilder, context);

        return notificationsBuilder.build();
    }

    private void populateNextFutureNotificationDate(final DateTime notificationDateTime, final Set<UUID> subscriptionIds, final FutureAccountNotificationsBuilder notificationsBuilder, final InternalCallContext context) {
        final LocalDate notificationDate = context.toLocalDate(notificationDateTime);
        notificationsBuilder.setNotificationListForTrigger(ImmutableMap.<LocalDate, Set<UUID>>of(notificationDate, subscriptionIds));
    }

    private FutureAccountNotifications createNextFutureNotificationDate(final InvoiceWithMetadata invoiceWithMetadata, final BillingEventSet billingEvents, final InternalCallContext context) {
        final FutureAccountNotificationsBuilder notificationsBuilder = new FutureAccountNotificationsBuilder();
        populateNextFutureNotificationDate(invoiceWithMetadata, notificationsBuilder);
        populateNextFutureDryRunNotificationDate(billingEvents, notificationsBuilder, context);
        return notificationsBuilder.build();
    }

    private void populateNextFutureNotificationDate(final InvoiceWithMetadata invoiceWithMetadata,
                                                    final FutureAccountNotificationsBuilder notificationsBuilder) {
        final Map<LocalDate, Set<UUID>> notificationListForTrigger = new HashMap<LocalDate, Set<UUID>>();
        for (final UUID subscriptionId : invoiceWithMetadata.getPerSubscriptionFutureNotificationDates().keySet()) {

            final SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = invoiceWithMetadata.getPerSubscriptionFutureNotificationDates().get(subscriptionId);

            if (subscriptionFutureNotificationDates.getNextRecurringDate() != null) {
                Set<UUID> subscriptionsForDates = notificationListForTrigger.get(subscriptionFutureNotificationDates.getNextRecurringDate());
                if (subscriptionsForDates == null) {
                    subscriptionsForDates = new HashSet<UUID>();
                    notificationListForTrigger.put(subscriptionFutureNotificationDates.getNextRecurringDate(), subscriptionsForDates);
                }
                subscriptionsForDates.add(subscriptionId);
            }

            if (subscriptionFutureNotificationDates.getNextUsageDates() != null) {
                for (final UsageDef usageDef : subscriptionFutureNotificationDates.getNextUsageDates().keySet()) {

                    final LocalDate nextNotificationDateForUsage = subscriptionFutureNotificationDates.getNextUsageDates().get(usageDef);
                    Set<UUID> subscriptionsForDates = notificationListForTrigger.get(nextNotificationDateForUsage);
                    if (subscriptionsForDates == null) {
                        subscriptionsForDates = new HashSet<UUID>();
                        notificationListForTrigger.put(nextNotificationDateForUsage, subscriptionsForDates);
                    }
                    subscriptionsForDates.add(subscriptionId);
                }
            }
        }
        notificationsBuilder.setNotificationListForTrigger(notificationListForTrigger);
    }

    private void populateNextFutureDryRunNotificationDate(final BillingEventSet billingEvents, final FutureAccountNotificationsBuilder notificationsBuilder, final InternalCallContext context) {


        final Map<LocalDate, Set<UUID>> notificationListForTrigger = notificationsBuilder.getNotificationListForTrigger();

        final long dryRunNotificationTime = invoiceConfig.getDryRunNotificationSchedule(context).getMillis();
        final boolean isInvoiceNotificationEnabled = dryRunNotificationTime > 0;

        final Map<LocalDate, Set<UUID>> notificationListForDryRun = isInvoiceNotificationEnabled ? new HashMap<LocalDate, Set<UUID>>() : ImmutableMap.<LocalDate, Set<UUID>>of();
        if (isInvoiceNotificationEnabled) {
            for (final LocalDate curDate : notificationListForTrigger.keySet()) {
                final LocalDate curDryRunDate = context.toLocalDate(context.toUTCDateTime(curDate).minus(dryRunNotificationTime));
                Set<UUID> subscriptionsForDryRunDates = notificationListForDryRun.get(curDryRunDate);
                if (subscriptionsForDryRunDates == null) {
                    subscriptionsForDryRunDates = new HashSet<UUID>();
                    notificationListForDryRun.put(curDryRunDate, subscriptionsForDryRunDates);
                }
                subscriptionsForDryRunDates.addAll(notificationListForTrigger.get(curDate));
            }

            final Map<UUID, DateTime> upcomingTransitionsForSubscriptions = isInvoiceNotificationEnabled ?
                                                                       getNextTransitionsForSubscriptions(billingEvents) :
                                                                       ImmutableMap.<UUID, DateTime>of();

            for (UUID curId : upcomingTransitionsForSubscriptions.keySet()) {
                final LocalDate curDryRunDate = context.toLocalDate(upcomingTransitionsForSubscriptions.get(curId).minus(dryRunNotificationTime));
                Set<UUID> subscriptionsForDryRunDates = notificationListForDryRun.get(curDryRunDate);
                if (subscriptionsForDryRunDates == null) {
                    subscriptionsForDryRunDates = new HashSet<UUID>();
                    notificationListForDryRun.put(curDryRunDate, subscriptionsForDryRunDates);
                }
                subscriptionsForDryRunDates.add(curId);
            }
        }
        notificationsBuilder.setNotificationListForDryRun(notificationListForDryRun);
    }

    private List<InvoiceItemModelDao> transformToInvoiceModelDao(final List<InvoiceItem> invoiceItems) {
        return Lists.transform(invoiceItems,
                               new Function<InvoiceItem, InvoiceItemModelDao>() {
                                   @Override
                                   public InvoiceItemModelDao apply(final InvoiceItem input) {
                                       return new InvoiceItemModelDao(input);
                                   }
                               });
    }

    private void logInvoiceWithItems(final ImmutableAccountData account, final Invoice invoice, final LocalDate targetDate) {
        final StringBuilder tmp = new StringBuilder();
        // Invoice plugins can generate items on new invoices or on existing invoices (e.g. adjustments)
        final String prefix = invoice.getInvoiceNumber() == null ? "Generated" : "Updated";
        tmp.append(String.format("%s invoiceId='%s', numberOfItems='%d', accountId='%s', targetDate='%s':", prefix, invoice.getId(), invoice.getNumberOfItems(), account.getId(), targetDate));
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            tmp.append(String.format("\n\t item = %s", item));
        }
        log.info(tmp.toString());
    }

    private void setFutureNotifications(final ImmutableAccountData account,
                                        final FutureAccountNotifications futureAccountNotifications,
                                        final InternalCallContext context) {
        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, context);
    }

    private void commitInvoiceAndSetFutureNotifications(final List<InvoiceModelDao> invoiceModelDaos,
                                                        final BillingEventSet billingEvents,
                                                        final Set<InvoiceTrackingModelDao> trackingIds,
                                                        final FutureAccountNotifications futureAccountNotifications,
                                                        final ExistingInvoiceMetadata existingInvoiceMetadata,
                                                        final InternalCallContext context) {
        invoiceDao.createInvoices(invoiceModelDaos, billingEvents, trackingIds, futureAccountNotifications, existingInvoiceMetadata, context);
    }

    private InvoiceItem computeCBAOnExistingInvoice(final Invoice invoice, final InternalCallContext context) throws InvoiceApiException {
        // Transformation to Invoice -> InvoiceModelDao
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        final List<InvoiceItemModelDao> invoiceItemModelDaos = ImmutableList.copyOf(Collections2.transform(invoice.getInvoiceItems(),
                                                                                                           new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                                                                               @Override
                                                                                                               public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                                                                   return new InvoiceItemModelDao(input);
                                                                                                               }
                                                                                                           }));
        invoiceModelDao.addInvoiceItems(invoiceItemModelDaos);
        final InvoiceItemModelDao cbaItem = invoiceDao.doCBAComplexity(invoiceModelDao, context);
        return cbaItem != null ? InvoiceItemFactory.fromModelDao(cbaItem) : null;
    }

    private TenantContext buildTenantContext(final InternalTenantContext context) {
        return internalCallContextFactory.createTenantContext(context);
    }

    private CallContext buildCallContext(final InternalCallContext context) {
        return internalCallContextFactory.createCallContext(context);
    }

    public void setChargedThroughDates(final Invoice invoice, final InternalCallContext context) throws SubscriptionBaseApiException {
        // Don't use invoice.getInvoiceItems(final Class<T> clazz) as some items can come from plugins
        final Collection<InvoiceItem> invoiceItemsToConsider = new LinkedList<InvoiceItem>();
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            switch (invoiceItem.getInvoiceItemType()) {
                case FIXED:
                case RECURRING:
                case USAGE:
                    invoiceItemsToConsider.add(invoiceItem);
                    break;
                default:
                    break;
            }
        }

        final Map<UUID, DateTime> chargeThroughDates = new HashMap<UUID, DateTime>();
        addInvoiceItemsToChargeThroughDates(chargeThroughDates, invoiceItemsToConsider, context);

        for (final UUID subscriptionId : chargeThroughDates.keySet()) {
            if (subscriptionId != null) {
                final DateTime chargeThroughDate = chargeThroughDates.get(subscriptionId);
                subscriptionApi.setChargedThroughDate(subscriptionId, chargeThroughDate, context);
            }
        }
    }

    private void postEvent(final BusInternalEvent event) {
        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private void addInvoiceItemsToChargeThroughDates(final Map<UUID, DateTime> chargeThroughDates,
                                                     final Collection<InvoiceItem> items,
                                                     final InternalTenantContext internalTenantContext) {

        for (final InvoiceItem item : items) {
            final UUID subscriptionId = item.getSubscriptionId();
            final LocalDate endDate = (item.getEndDate() != null) ? item.getEndDate() : item.getStartDate();

            final DateTime proposedChargedThroughDate = internalTenantContext.toUTCDateTime(endDate);
            if (chargeThroughDates.containsKey(subscriptionId)) {
                if (chargeThroughDates.get(subscriptionId).isBefore(proposedChargedThroughDate)) {
                    chargeThroughDates.put(subscriptionId, proposedChargedThroughDate);
                }
            } else {
                chargeThroughDates.put(subscriptionId, proposedChargedThroughDate);
            }
        }
    }

    public static class FutureAccountNotifications {

        private final Map<LocalDate, Set<UUID>> notificationListForTrigger;
        private final Map<LocalDate, Set<UUID>> notificationListForDryRun;
        private final boolean isRescheduled;

        public FutureAccountNotifications() {
            this(ImmutableMap.<LocalDate, Set<UUID>>of(), ImmutableMap.<LocalDate, Set<UUID>>of(), false);
        }

        public FutureAccountNotifications(final Map<LocalDate, Set<UUID>> notificationListForTrigger, final Map<LocalDate, Set<UUID>> notificationListForDryRun, final boolean isRescheduled) {
            this.notificationListForTrigger = notificationListForTrigger;
            this.notificationListForDryRun = notificationListForDryRun;
            this.isRescheduled = isRescheduled;
        }

        public Map<LocalDate, Set<UUID>> getNotificationsForTrigger() {
            return notificationListForTrigger;
        }

        public Map<LocalDate, Set<UUID>> getNotificationsForDryRun() {
            return notificationListForDryRun;
        }

        public boolean isRescheduled() {
            return isRescheduled;
        }

        public static class FutureAccountNotificationsBuilder {

            private Map<LocalDate, Set<UUID>> notificationListForTrigger;
            private Map<LocalDate, Set<UUID>> notificationListForDryRun;
            private boolean isRescheduled = false;

            public FutureAccountNotificationsBuilder() {
            }

            public FutureAccountNotificationsBuilder setNotificationListForTrigger(final Map<LocalDate, Set<UUID>> notificationListForTrigger) {
                this.notificationListForTrigger = notificationListForTrigger;
                return this;
            }

            public FutureAccountNotificationsBuilder setNotificationListForDryRun(final Map<LocalDate, Set<UUID>> notificationListForDryRun) {
                this.notificationListForDryRun = notificationListForDryRun;
                return this;
            }

            public FutureAccountNotificationsBuilder setRescheduled(final boolean rescheduled) {
                isRescheduled = rescheduled;
                return this;
            }

            public Map<LocalDate, Set<UUID>> getNotificationListForTrigger() {
                return MoreObjects.firstNonNull(notificationListForTrigger, ImmutableMap.<LocalDate, Set<UUID>>of());
            }

            public Map<LocalDate, Set<UUID>> getNotificationListForDryRun() {
                return MoreObjects.firstNonNull(notificationListForDryRun, ImmutableMap.<LocalDate, Set<UUID>>of());
            }

            public boolean isRescheduled() {
                return isRescheduled;
            }

            public FutureAccountNotifications build() {
                return new FutureAccountNotifications(getNotificationListForTrigger(), getNotificationListForDryRun(), isRescheduled());
            }
        }
    }

    private Set<LocalDate> getUpcomingInvoiceCandidateDates(final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications,
                                                             final Map<UUID, DateTime> nextScheduledSubscriptionsEventMap,
                                                             final Iterable<UUID> filteredSubscriptionIds,
                                                             final InternalCallContext internalCallContext) {

        final Iterable<DateTime> nextScheduledInvoiceDates = getNextScheduledInvoiceEffectiveDate(futureNotifications, filteredSubscriptionIds);

        final Iterable<DateTime> nextScheduledSubscriptionsEvents;
        if (!Iterables.isEmpty(filteredSubscriptionIds)) {
            List<DateTime> tmp = new ArrayList<DateTime>();
            for (final UUID curSubscriptionId : nextScheduledSubscriptionsEventMap.keySet()) {
                if (Iterables.contains(filteredSubscriptionIds, curSubscriptionId)) {
                    tmp.add(nextScheduledSubscriptionsEventMap.get(curSubscriptionId));
                }
            }
            nextScheduledSubscriptionsEvents = tmp;
        } else {
            nextScheduledSubscriptionsEvents = nextScheduledSubscriptionsEventMap.values();
        }

        return Sets.newTreeSet(Iterables.transform(Iterables.<DateTime>concat(nextScheduledInvoiceDates, nextScheduledSubscriptionsEvents),
                            new Function<DateTime, LocalDate>() {
                                @Override
                                public LocalDate apply(final DateTime input) {
                                    return internalCallContext.toLocalDate(input);
                                }
                            }));
    }

    private Iterable<DateTime> getNextScheduledInvoiceEffectiveDate(final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications,
                                                                    final Iterable<UUID> filteredSubscriptionIds) {
        final Collection<DateTime> effectiveDates = new LinkedList<DateTime>();
        for (final NotificationEventWithMetadata<NextBillingDateNotificationKey> input : futureNotifications) {

            // If we don't specify a filter list of subscriptionIds, we look at all events.
            boolean isEventForSubscription = Iterables.isEmpty(filteredSubscriptionIds);
            // If we specify a filter, we keep the date if at least one of the subscriptions from the event list matches one of the subscription from our filter list
            if (!Iterables.isEmpty(filteredSubscriptionIds)) {
                for (final UUID curSubscriptionId : filteredSubscriptionIds) {
                    if (Iterables.contains(input.getEvent().getUuidKeys(), curSubscriptionId)) {
                        isEventForSubscription = true;
                        break;
                    }
                }
            }

            final boolean isEventDryRunForNotifications = input.getEvent().isDryRunForInvoiceNotification() != null ?
                                                          input.getEvent().isDryRunForInvoiceNotification() : false;
            if (isEventForSubscription && !isEventDryRunForNotifications) {
                effectiveDates.add(input.getEffectiveDate());
            }
        }
        return effectiveDates;
    }

    private static final class TargetDateDryRunArguments implements DryRunArguments {

        @Override
        public DryRunType getDryRunType() {
            return DryRunType.TARGET_DATE;
        }

        @Override
        public EntitlementSpecifier getEntitlementSpecifier() {
            return null;
        }

        @Override
        public SubscriptionEventType getAction() {
            return null;
        }

        @Override
        public UUID getSubscriptionId() {
            return null;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return null;
        }

        @Override
        public UUID getBundleId() {
            return null;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return null;
        }


        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TargetDateDryRunArguments{");
            sb.append("dryRunType=").append(DryRunType.TARGET_DATE);
            sb.append('}');
            return sb.toString();
        }
    }

    public void processParentInvoiceForInvoiceGeneration(final Account childAccount, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), childAccount.getParentAccountId().toString(), invoiceConfig.getMaxGlobalLockRetries());

            processParentInvoiceForInvoiceGenerationWithLock(childAccount, childInvoiceId, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process parent invoice for parentAccountId='{}'", childAccount.getParentAccountId().toString(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    private void processParentInvoiceForInvoiceGenerationWithLock(final Account childAccount, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        log.info("Processing parent invoice for parentAccountId='{}', childInvoiceId='{}'", childAccount.getParentAccountId(), childInvoiceId);
        final InvoiceModelDao childInvoiceModelDao = invoiceDao.getById(childInvoiceId, context);
        final Invoice childInvoice = new DefaultInvoice(childInvoiceModelDao);

        final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(childAccount.getParentAccountId(), ObjectType.ACCOUNT, buildTenantContext(context));
        final InternalCallContext parentContext = internalCallContextFactory.createInternalCallContext(parentAccountRecordId, context);

        BigDecimal childInvoiceAmount = InvoiceCalculatorUtils.computeChildInvoiceAmount(childInvoice.getCurrency(), childInvoice.getInvoiceItems());
        InvoiceModelDao draftParentInvoice = invoiceDao.getParentDraftInvoice(childAccount.getParentAccountId(), parentContext);

        final String description = childAccount.getExternalKey().concat(" summary");
        if (draftParentInvoice != null) {
            for (InvoiceItemModelDao item : draftParentInvoice.getInvoiceItems()) {
                if ((item.getChildAccountId() != null) && item.getChildAccountId().equals(childInvoice.getAccountId())) {
                    // update child item amount for existing parent invoice item
                    BigDecimal newChildInvoiceAmount = childInvoiceAmount.add(item.getAmount());
                    log.info("Updating existing itemId='{}', oldAmount='{}', newAmount='{}' on existing DRAFT invoiceId='{}'", item.getId(), item.getAmount(), newChildInvoiceAmount, draftParentInvoice.getId());
                    invoiceDao.updateInvoiceItemAmount(item.getId(), newChildInvoiceAmount, parentContext);
                    return;
                }
            }

            // new item when the parent invoices does not have this child item yet
            final ParentInvoiceItem newParentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), context.getCreatedDate(), draftParentInvoice.getId(), childAccount.getParentAccountId(), childAccount.getId(), childInvoiceAmount, childAccount.getCurrency(), description);
            final InvoiceItemModelDao parentInvoiceItem = new InvoiceItemModelDao(newParentInvoiceItem);
            draftParentInvoice.addInvoiceItem(parentInvoiceItem);

            List<InvoiceModelDao> invoices = new ArrayList<InvoiceModelDao>();
            invoices.add(draftParentInvoice);
            log.info("Adding new itemId='{}', amount='{}' on existing DRAFT invoiceId='{}'", parentInvoiceItem.getId(), childInvoiceAmount, draftParentInvoice.getId());
            invoiceDao.createInvoices(invoices, parentContext);
        } else {
            if (shouldIgnoreChildInvoice(childInvoice, childInvoiceAmount)) {
                return;
            }

            final LocalDate invoiceDate = context.toLocalDate(context.getCreatedDate());
            draftParentInvoice = new InvoiceModelDao(childAccount.getParentAccountId(), invoiceDate, childAccount.getCurrency(), InvoiceStatus.DRAFT, true);
            final InvoiceItem parentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), context.getCreatedDate(), draftParentInvoice.getId(), childAccount.getParentAccountId(), childAccount.getId(), childInvoiceAmount, childAccount.getCurrency(), description);
            draftParentInvoice.addInvoiceItem(new InvoiceItemModelDao(parentInvoiceItem));

            log.info("Adding new itemId='{}', amount='{}' on new DRAFT invoiceId='{}'", parentInvoiceItem.getId(), childInvoiceAmount, draftParentInvoice.getId());
            invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(draftParentInvoice), parentContext);
        }

        // save parent child invoice relation
        final InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(draftParentInvoice.getId(), childInvoiceId, childAccount.getId());
        invoiceDao.createParentChildInvoiceRelation(invoiceRelation, parentContext);
    }

    private boolean shouldIgnoreChildInvoice(final Invoice childInvoice, final BigDecimal childInvoiceAmount) {

        switch (childInvoiceAmount.compareTo(BigDecimal.ZERO)) {
            case -1:
                // do nothing if child invoice has negative amount because it's a credit and it will be use in next invoice
                return true;
            case 1:
                return false;
            case 0:
                // only ignore if amount == 0 and any item is not FIXED or RECURRING
                for (InvoiceItem item : childInvoice.getInvoiceItems()) {
                    if (item.getInvoiceItemType().equals(InvoiceItemType.FIXED) || item.getInvoiceItemType().equals(InvoiceItemType.RECURRING)) {
                        return false;
                    }
                }
        }

        return true;
    }

    public void processParentInvoiceForAdjustments(final Account childAccount, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), childAccount.getParentAccountId().toString(), invoiceConfig.getMaxGlobalLockRetries());

            processParentInvoiceForAdjustmentsWithLock(childAccount, childInvoiceId, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process parent invoice for parentAccountId='{}'", childAccount.getParentAccountId().toString(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    public void processParentInvoiceForAdjustmentsWithLock(final Account account, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        final InvoiceModelDao childInvoiceModelDao = invoiceDao.getById(childInvoiceId, context);
        final InvoiceModelDao parentInvoiceModelDao = childInvoiceModelDao.getParentInvoice();

        if (parentInvoiceModelDao == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_MISSING_PARENT_INVOICE, childInvoiceModelDao.getId());
        } else if (InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(parentInvoiceModelDao).compareTo(BigDecimal.ZERO) == 0) {
            // ignore item adjustments for paid invoices.
            return;
        }

        final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(account.getParentAccountId(), ObjectType.ACCOUNT, buildTenantContext(context));
        final InternalCallContext parentContext = internalCallContextFactory.createInternalCallContext(parentAccountRecordId, context);
        final String description = "Adjustment for account ".concat(account.getExternalKey());

        // find PARENT_SUMMARY invoice item for this child account
        final InvoiceItemModelDao parentSummaryInvoiceItem = Iterables.find(parentInvoiceModelDao.getInvoiceItems(), new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(@Nullable final InvoiceItemModelDao input) {
                return input.getType().equals(InvoiceItemType.PARENT_SUMMARY)
                       && input.getChildAccountId().equals(childInvoiceModelDao.getAccountId());
            }
        });

        final Iterable<InvoiceItemModelDao> childAdjustments = Iterables.filter(childInvoiceModelDao.getInvoiceItems(), new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(@Nullable final InvoiceItemModelDao input) {
                return input.getType().equals(InvoiceItemType.ITEM_ADJ);
            }
        });

        //  childAdjustments can be empty if event was a result of a CBA_ADJ
        if (Iterables.isEmpty(childAdjustments)) {
            return;
        }


        // find last ITEM_ADJ invoice added in child invoice
        final InvoiceItemModelDao lastChildInvoiceItemAdjustment = Collections.max(Lists.newArrayList(childAdjustments), new Comparator<InvoiceItemModelDao>() {
            @Override
            public int compare(InvoiceItemModelDao o1, InvoiceItemModelDao o2) {
                return o1.getCreatedDate().compareTo(o2.getCreatedDate());
            }
        });

        final BigDecimal childInvoiceAdjustmentAmount = lastChildInvoiceItemAdjustment.getAmount();

        if (parentInvoiceModelDao.getStatus().equals(InvoiceStatus.COMMITTED)) {
            ItemAdjInvoiceItem adj = new ItemAdjInvoiceItem(UUIDs.randomUUID(),
                                                            lastChildInvoiceItemAdjustment.getCreatedDate(),
                                                            parentSummaryInvoiceItem.getInvoiceId(),
                                                            parentSummaryInvoiceItem.getAccountId(),
                                                            lastChildInvoiceItemAdjustment.getStartDate(),
                                                            description,
                                                            childInvoiceAdjustmentAmount,
                                                            parentInvoiceModelDao.getCurrency(),
                                                            parentSummaryInvoiceItem.getId(),
                                                            null);
            parentInvoiceModelDao.addInvoiceItem(new InvoiceItemModelDao(adj));
            invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(parentInvoiceModelDao), parentContext);
            return;
        }

        // update item amount
        final BigDecimal newParentInvoiceItemAmount = childInvoiceAdjustmentAmount.add(parentSummaryInvoiceItem.getAmount());
        invoiceDao.updateInvoiceItemAmount(parentSummaryInvoiceItem.getId(), newParentInvoiceItemAmount, parentContext);
    }

    private static class InvoicesWithFutureNotifications {

        private final Iterable<Invoice> invoices;
        private final FutureAccountNotifications notifications;

        public InvoicesWithFutureNotifications(final Iterable<DefaultInvoice> invoices, final FutureAccountNotifications notifications) {
            Preconditions.checkState(invoices != null && !Iterables.<Invoice>isEmpty(invoices), "invoices cannot be null or empty");
            this.invoices = ImmutableList.<Invoice>copyOf(invoices);
            this.notifications = notifications;
        }

        public Iterable<Invoice> getInvoices() {
            return invoices;
        }

        public FutureAccountNotifications getNotifications() {
            return notifications;
        }
    }
}
