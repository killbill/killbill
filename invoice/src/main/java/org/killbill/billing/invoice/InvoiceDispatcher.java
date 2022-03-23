/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.billing.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
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
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.events.RequestedSubscriptionInternalEvent;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.FutureAccountNotificationsBuilder;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunInfo;
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
import org.killbill.billing.invoice.optimizer.InvoiceOptimizer;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;
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
import org.killbill.billing.util.optimizer.BusOptimizer;
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

    private static final long NANO_TO_MILLI_SEC = (1000L * 1000L);
    public static final int MAX_NB_ITEMS_TO_PRINT = 20;

    private static final Ordering<DateTime> UPCOMING_NOTIFICATION_DATE_ORDERING = Ordering.natural();
    private static final Joiner JOINER_COMMA = Joiner.on(",");
    private static final TargetDateDryRunArguments TARGET_DATE_DRY_RUN_ARGUMENTS = new TargetDateDryRunArguments();

    private static final String DRY_RUN_CUR_DATE_PROP = "DRY_RUN_CUR_DATE";
    private static final String DRY_RUN_TARGET_DATE_PROP = "DRY_RUN_TARGET_DATE";

    private final InvoiceGenerator generator;
    private final BillingInternalApi billingApi;
    private final AccountInternalApi accountApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceDao invoiceDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final GlobalLocker locker;
    private final BusOptimizer eventBus;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final InvoiceConfig invoiceConfig;
    private final ParkedAccountsManager parkedAccountsManager;
    private final InvoiceOptimizer invoiceOptimizer;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator,
                             final AccountInternalApi accountApi,
                             final BillingInternalApi billingApi,
                             final SubscriptionBaseInternalApi SubscriptionApi,
                             final InvoiceDao invoiceDao,
                             final InternalCallContextFactory internalCallContextFactory,
                             final InvoicePluginDispatcher invoicePluginDispatcher,
                             final GlobalLocker locker,
                             final BusOptimizer eventBus,
                             final NotificationQueueService notificationQueueService,
                             final InvoiceConfig invoiceConfig,
                             final Clock clock,
                             final InvoiceOptimizer invoiceOptimizer,
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
        this.invoiceOptimizer = invoiceOptimizer;
        this.notificationQueueService = notificationQueueService;
        this.invoiceConfig = invoiceConfig;
        this.parkedAccountsManager = parkedAccountsManager;
    }

    public void processAccountBCDChange(final UUID accountId, final InternalCallContext internalCallContext) {
        try {
            processAccount(false, accountId, null, null, false, internalCallContext);
        } catch (final InvoiceApiException e) {
            log.warn("Failed to process BCD change for accountId='{}'", accountId, e);
        }
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
            // TODO
            // Can we use cutoffDt ?
            // Do we even need the billing events ?
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, null, null, context);
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

    public void processSubscriptionForInvoiceGeneration(final EffectiveSubscriptionInternalEvent transition,
                                                        final InternalCallContext context) throws InvoiceApiException {
        final LocalDate targetDate = context.toLocalDate(transition.getEffectiveTransitionTime());
        processSubscriptionForInvoiceGeneration(targetDate, false, context);
    }

    public void processSubscriptionForInvoiceGeneration(final LocalDate targetDate, final boolean isRescheduled, final InternalCallContext context) throws InvoiceApiException {
        processSubscriptionInternal(targetDate, false, isRescheduled, context);
    }

    public void processSubscriptionForInvoiceNotification(final LocalDate targetDate, final InternalCallContext context) throws InvoiceApiException {
        final Invoice dryRunInvoice = processSubscriptionInternal(targetDate, true, false, context);
        if (dryRunInvoice != null && dryRunInvoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            final InvoiceNotificationInternalEvent event = new DefaultInvoiceNotificationInternalEvent(dryRunInvoice.getAccountId(), dryRunInvoice.getBalance(), dryRunInvoice.getCurrency(),
                                                                                                       context.toUTCDateTime(targetDate), context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
            try {
                eventBus.post(event);
            } catch (final EventBusException e) {
                log.warn("Failed to post event {}", event, e);
            }
        }
    }

    private Invoice processSubscriptionInternal(final LocalDate targetDate, final boolean dryRunForNotification, final boolean isRescheduled, final InternalCallContext context) throws InvoiceApiException {
        final CallContext callContext = internalCallContextFactory.createCallContext(context);
        final UUID accountId = callContext.getAccountId();
        final DryRunArguments dryRunArguments = dryRunForNotification ? TARGET_DATE_DRY_RUN_ARGUMENTS : null;
        return processAccountFromNotificationOrBusEvent(accountId, targetDate, dryRunArguments, isRescheduled, context);
    }

    public Invoice processAccountFromNotificationOrBusEvent(final UUID accountId,
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


    public Invoice processAccount(final boolean isApiCall,
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


        if (!isApiCall &&
            !locker.isFree(LockerType.ACCNT_INV_PAY.toString(), accountId.toString())) {
            if (invoiceOptimizer.rescheduleProcessAccount(accountId, context)) {
                return null;
            }
        }

        GlobalLock lock = null;
        try {
            // Grab lock unless we do a dry-run
            final boolean isDryRun = dryRunArguments != null;
            lock = !isDryRun ? locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), invoiceConfig.getMaxGlobalLockRetries()) : null;
            return processAccountInternal(isApiCall, parkedAccount, accountId, targetDate, dryRunArguments, isRescheduled, context);
        } catch (final LockFailedException e) {
            if (isApiCall) {
                throw new InvoiceApiException(e, ErrorCode.UNEXPECTED_ERROR, "Failed to generate invoice: failed to acquire lock");
            }
            if (!invoiceOptimizer.rescheduleProcessAccount(accountId, context)) {
                log.warn("Failed to process invoice for accountId='{}', targetDate='{}'", accountId.toString(), targetDate, e);
            }
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private enum InvoiceTiming {
        BILLING_EVENTS,
        FETCH_INVOICES,
        INVOICE_GENERATION,
        PLUGINS_PRIOR_CALL,
        PLUGINS_ADDITIONAL_ITEMS,
        PLUGINS_COMPLETION_CALL,
        COMMIT_INVOICE,
        SET_CHARGE_THROUGH_DT,
    }

    private Invoice processAccountInternal(final boolean isApiCall,
                                           final boolean parkedAccount,
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
        // Passed through invoice code to be propagated to usage module/plugins
        final LocalDate dryRunInfoDate = isDryRun && dryRunArguments.getDryRunType() == DryRunType.SUBSCRIPTION_ACTION ? dryRunArguments.getEffectiveDate() : inputTargetDate;
        final DryRunInfo dryRunInfo = isDryRun ? new DryRunInfo(dryRunArguments.getDryRunType(), dryRunInfoDate) : null;

        final Map<InvoiceTiming, Long> invoiceTimings = new HashMap<>();
        try {

            long startNano = System.nanoTime();
            final AccountInvoices accountInvoices = invoiceOptimizer.getInvoices(context);
            invoiceTimings.put(InvoiceTiming.FETCH_INVOICES, System.nanoTime() - startNano);

            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            startNano = System.nanoTime();
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, dryRunArguments, accountInvoices.getBillingEventCutoffDate(), context);
            invoiceTimings.put(InvoiceTiming.BILLING_EVENTS, System.nanoTime() - startNano);
            if (!isApiCall && billingEvents.isAccountAutoInvoiceOff()) {
                return null;
            }

            final Invoice invoice;
            if (!isDryRun) {
                final InvoiceWithFutureNotifications invoiceWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, inputTargetDate, billingEvents, accountInvoices, dryRunInfo, isRescheduled, Lists.newLinkedList(), invoiceTimings, context);
                invoice = invoiceWithFutureNotifications != null ? invoiceWithFutureNotifications.getInvoice() : null;
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
                        invoice = processDryRun_UPCOMING_INVOICE_Invoice(accountId, allCandidateTargetDates, billingEvents, accountInvoices, dryRunInfo, invoiceTimings, context);
                    } else {
                        invoice = processDryRun_UPCOMING_INVOICE_FILTERING_Invoice(accountId, filteredCandidateTargetDates, allCandidateTargetDates, billingEvents, accountInvoices, dryRunInfo, invoiceTimings, context);
                    }
                } else /* DryRunType.TARGET_DATE, SUBSCRIPTION_ACTION */ {
                    invoice = processDryRun_TARGET_DATE_Invoice(accountId, inputTargetDate, allCandidateTargetDates, billingEvents, accountInvoices, dryRunInfo, invoiceTimings, context);
                }
            }

            printInvoiceTiming(invoiceTimings);
            return invoice;
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
            throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR, "Failed to retrieve future notifications from notificationQ");
        }
    }

    private void printInvoiceTiming(final Map<InvoiceTiming, Long> invoiceTimings) {
        boolean first = true;
        final StringBuilder tmp = new StringBuilder("Invoice timings: ");
        for (final InvoiceTiming key : InvoiceTiming.values()) {
            if (!first) {
                tmp.append(", ");
            }
            tmp.append(key);
            tmp.append("=");
            final Long val = invoiceTimings.get(key) != null ? invoiceTimings.get(key) / NANO_TO_MILLI_SEC : 0L;
            tmp.append(val);
            tmp.append(" mSec");
            first = false;
        }
        log.info(tmp.toString());
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

    private Invoice processDryRun_UPCOMING_INVOICE_Invoice(final UUID accountId, final Set<LocalDate> allCandidateTargetDates, final BillingEventSet billingEvents, final AccountInvoices accountInvoices, final DryRunInfo dryRunInfo, final Map<InvoiceTiming, Long> invoiceTimings, final InternalCallContext context) throws InvoiceApiException {
        for (final LocalDate curTargetDate : allCandidateTargetDates) {
            final InvoiceWithFutureNotifications invoiceWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, curTargetDate, billingEvents, accountInvoices, dryRunInfo, false, Lists.newLinkedList(), invoiceTimings, context);
            final Invoice invoice = invoiceWithFutureNotifications != null ? invoiceWithFutureNotifications.getInvoice() : null;
            if (invoice != null) {
                return invoice;
            }
        }
        return null;
    }

    private Invoice processDryRun_UPCOMING_INVOICE_FILTERING_Invoice(final UUID accountId, final Set<LocalDate> filteringCandidateTargetDates, final Set<LocalDate> allCandidateTargetDates, final BillingEventSet billingEvents, final AccountInvoices accountInvoices, final DryRunInfo dryRunInfo, final Map<InvoiceTiming, Long> invoiceTimings, final InternalCallContext context) throws InvoiceApiException {
        for (final LocalDate curTargetDate : filteringCandidateTargetDates) {
            final Invoice invoice = processDryRun_TARGET_DATE_Invoice(accountId, curTargetDate, allCandidateTargetDates, billingEvents, accountInvoices, dryRunInfo, invoiceTimings, context);
            if (invoice != null) {
                return invoice;
            }
        }
        return null;
    }

    private Invoice processDryRun_TARGET_DATE_Invoice(final UUID accountId, final LocalDate targetDate, final Set<LocalDate> allCandidateTargetDates, final BillingEventSet billingEvents, final AccountInvoices accountInvoices, final DryRunInfo dryRunInfo, final Map<InvoiceTiming, Long> invoiceTimings, final InternalCallContext context) throws InvoiceApiException {

        LinkedList<PluginProperty> pluginProperties;

        final PriorityQueue<LocalDate> pq = new PriorityQueue<LocalDate>(allCandidateTargetDates);

        // Keeps track of generated invoices as we go through the list
        // The list is an ordered list of items merged from existing notifications and upcoming notifications, each of these the result of a previous invoice being generated.
        // Note: we reuse the underlying list from  the AccountInvoices to avoid recreating such object (which is feature dependent)
        //
        final List<Invoice> augmentedExistingInvoices = accountInvoices.getInvoices();
        Invoice additionalInvoice = null;
        LocalDate prev = null;
        LocalDate cur;
        while ((cur = pq.poll()) != null) {
            // Eat up duplicate if any
            if (prev != null && prev.compareTo(cur) == 0) {
                continue;
            }
            if (cur.compareTo(targetDate) > 0) {
                break;
            }

            // Loop through each boundary date prior to our given targetDate
            pluginProperties = new LinkedList<PluginProperty>();
            pluginProperties.add(new PluginProperty(DRY_RUN_CUR_DATE_PROP, cur, false));
            pluginProperties.add(new PluginProperty(DRY_RUN_TARGET_DATE_PROP, targetDate, false));

            final InvoiceWithFutureNotifications result = processAccountWithLockAndInputTargetDate(accountId, cur, billingEvents, accountInvoices, dryRunInfo, false, pluginProperties, invoiceTimings, context);
            additionalInvoice = result != null ? result.getInvoice() : null;
            if (additionalInvoice != null) {
                for (final LocalDate k : result.getNotifications().getNotificationsForTrigger().keySet()) {
                    if (k.compareTo(cur) > 0 && k.compareTo(targetDate) < 0) {
                        pq.add(k);
                    }
                }
                // In order to handle AUTO_INVOICING_REUSE_DRAFT where the invoice is being reused
                // we need to only keep the latest invoice with all the items currently being generated
                // See https://github.com/killbill/killbill/issues/1313
                final UUID additionalInvoiceId = additionalInvoice.getId();
                Iterables.removeIf(augmentedExistingInvoices, new Predicate<Invoice>() {
                    @Override
                    public boolean apply(final Invoice input) {
                        return input.getId().equals(additionalInvoiceId);
                    }
                });
                augmentedExistingInvoices.add(additionalInvoice);
            }
            prev = cur;
        }

        final boolean isTargetDateAlignedOnLastTransition = prev != null && prev.compareTo(targetDate) == 0;
        if (isTargetDateAlignedOnLastTransition) {
            return additionalInvoice;
        } else { /* The provided targetDate does not coincide with any transition, so we try it and return what we find if there is anything or default to previous transition  */
            pluginProperties = new LinkedList<PluginProperty>();
            pluginProperties.add(new PluginProperty(DRY_RUN_CUR_DATE_PROP, targetDate, false));
            pluginProperties.add(new PluginProperty(DRY_RUN_TARGET_DATE_PROP, targetDate, false));
            final InvoiceWithFutureNotifications invoiceWithFutureNotifications = processAccountWithLockAndInputTargetDate(accountId, targetDate, billingEvents, accountInvoices, dryRunInfo, false, pluginProperties, invoiceTimings, context);
            final Invoice targetInvoice = invoiceWithFutureNotifications != null ? invoiceWithFutureNotifications.getInvoice() : null;
            return targetInvoice != null ? targetInvoice : additionalInvoice;
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

    private InvoiceWithFutureNotifications processAccountWithLockAndInputTargetDate(final UUID accountId,
                                                                                    final LocalDate originalTargetDate,
                                                                                    final BillingEventSet billingEvents,
                                                                                    final AccountInvoices accountInvoices,
                                                                                    @Nullable final DryRunInfo dryRunInfo,
                                                                                    final boolean isRescheduled,
                                                                                    final LinkedList<PluginProperty> pluginProperties,
                                                                                    final Map<InvoiceTiming, Long> invoiceTimings,
                                                                                    final InternalCallContext internalCallContext) throws InvoiceApiException {
        final boolean isDryRun = dryRunInfo != null;
        final CallContext callContext = buildCallContext(internalCallContext);

        final ImmutableAccountData account;
        try {
            account = accountApi.getImmutableAccountDataById(accountId, internalCallContext);
        } catch (final AccountApiException e) {
            log.error("Unable to generate invoice for accountId='{}', a future notification has NOT been recorded", accountId, e);
            long startNano = System.nanoTime();
            invoicePluginDispatcher.onFailureCall(originalTargetDate, null, accountInvoices.getInvoices(), isDryRun, isRescheduled, callContext, pluginProperties, internalCallContext);
            invoiceTimings.put(InvoiceTiming.PLUGINS_COMPLETION_CALL, System.nanoTime() - startNano);
            return null;
        }

        long startNano = System.nanoTime();
        final DateTime rescheduleDate = invoicePluginDispatcher.priorCall(originalTargetDate, accountInvoices.getInvoices(), isDryRun, isRescheduled, callContext, pluginProperties, internalCallContext);
        invoiceTimings.put(InvoiceTiming.PLUGINS_PRIOR_CALL, System.nanoTime() - startNano);

        if (rescheduleDate != null) {
            if (isDryRun) {
                log.warn("Ignoring rescheduleDate='{}', delayed scheduling is unsupported in dry-run", rescheduleDate);
            } else {
                final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(rescheduleDate, billingEvents, internalCallContext);
                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
            }
            return null;
        }

        startNano = System.nanoTime();
        final InvoiceWithMetadata invoiceWithMetadata = generateKillBillInvoice(account, originalTargetDate, billingEvents, accountInvoices, dryRunInfo, internalCallContext);
        invoiceTimings.put(InvoiceTiming.INVOICE_GENERATION, System.nanoTime() - startNano);


        final DefaultInvoice invoice = invoiceWithMetadata.getInvoice();

        // Compute future notifications
        final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(invoiceWithMetadata, billingEvents, internalCallContext);

        // If invoice comes back null, there is nothing new to generate, we can bail early
        if (invoice == null) {
            startNano = System.nanoTime();
            invoicePluginDispatcher.onSuccessCall(originalTargetDate, null, accountInvoices.getInvoices(), isDryRun, isRescheduled, callContext, pluginProperties, internalCallContext);
            invoiceTimings.put(InvoiceTiming.PLUGINS_COMPLETION_CALL, System.nanoTime() - startNano);

            if (isDryRun) {
                log.info("Generated null dryRun invoice for accountId='{}', targetDate='{}'", accountId, originalTargetDate);
            } else {
                log.info("Generated null invoice for accountId='{}', targetDate='{}'", accountId, originalTargetDate);

                final BusInternalEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(),
                                                                           internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(), internalCallContext.getUserToken());

                // Although we have a null invoice, it could be as a result of removing $0 USAGE (config#isUsageZeroAmountDisabled)
                // and so we may still need to set the CTD for such subscriptions.
                startNano = System.nanoTime();
                setChargedThroughDatesNoExceptions(invoiceWithMetadata.getChargeThroughDates(), internalCallContext);
                invoiceTimings.put(InvoiceTiming.SET_CHARGE_THROUGH_DT, System.nanoTime() - startNano);
                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
                postEvent(event);
            }
            return null;
        }

        final LocalDate actualTargetDate = invoice.getTargetDate();
        boolean success = false;
        try {
            // Generate missing credit (> 0 for generation and < 0 for use) prior we call the plugin(s)
            final InvoiceItem cbaItemPreInvoicePlugins = computeCBAOnExistingInvoice(invoice, internalCallContext);
            if (cbaItemPreInvoicePlugins != null) {
                invoice.addInvoiceItem(cbaItemPreInvoicePlugins);
            }

            //
            // Ask external invoice plugins if additional items (tax, etc) shall be added to the invoice
            //
            startNano = System.nanoTime();
            final boolean invoiceUpdated = invoicePluginDispatcher.updateOriginalInvoiceWithPluginInvoiceItems(invoice, isDryRun, callContext, pluginProperties, internalCallContext);
            invoiceTimings.put(InvoiceTiming.PLUGINS_ADDITIONAL_ITEMS, System.nanoTime() - startNano);

            if (invoiceUpdated) {
                // Remove the temporary CBA item as we need to re-compute CBA
                if (cbaItemPreInvoicePlugins != null) {
                    invoice.removeInvoiceItemIfExists(cbaItemPreInvoicePlugins);
                }

                // Use credit after we call the plugin (https://github.com/killbill/killbill/issues/637)
                final InvoiceItem cbaItemPostInvoicePlugins = computeCBAOnExistingInvoice(invoice, internalCallContext);
                if (cbaItemPostInvoicePlugins != null) {
                    invoice.addInvoiceItem(cbaItemPostInvoicePlugins);
                }
            }

            if (!isDryRun) {
                // Compute whether this is a new invoice object (or just some adjustments on an existing invoice), and extract invoiceIds for later use
                final Set<UUID> uniqueInvoiceIds = getUniqueInvoiceIds(invoice);
                final boolean isRealInvoiceWithItems = uniqueInvoiceIds.remove(invoice.getId());
                final Set<UUID> adjustedUniqueOtherInvoiceId = uniqueInvoiceIds;

                logInvoiceWithItems(account, invoice, actualTargetDate, adjustedUniqueOtherInvoiceId, isRealInvoiceWithItems);

                // Transformation to Invoice -> InvoiceModelDao
                final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
                final List<InvoiceItemModelDao> invoiceItemModelDaos = transformToInvoiceModelDao(invoice.getInvoiceItems());
                invoiceModelDao.addInvoiceItems(invoiceItemModelDaos);

                final Set<InvoiceTrackingModelDao> trackingIds = new HashSet<>();
                for (final TrackingRecordId cur : invoiceWithMetadata.getTrackingIds()) {
                    trackingIds.add(new InvoiceTrackingModelDao(cur.getTrackingId(), cur.getInvoiceId(), cur.getSubscriptionId(), cur.getUnitType(), cur.getRecordDate()));
                }

                // Commit invoice on disk
                final ExistingInvoiceMetadata existingInvoiceMetadata = new ExistingInvoiceMetadata(accountInvoices.getInvoices());
                startNano = System.nanoTime();
                commitInvoiceAndSetFutureNotifications(account, invoiceModelDao, billingEvents, trackingIds, futureAccountNotifications, existingInvoiceMetadata, internalCallContext);
                invoiceTimings.put(InvoiceTiming.COMMIT_INVOICE, System.nanoTime() - startNano);

                startNano = System.nanoTime();
                setChargedThroughDatesNoExceptions(invoiceWithMetadata.getChargeThroughDates(), internalCallContext);
                invoiceTimings.put(InvoiceTiming.SET_CHARGE_THROUGH_DT, System.nanoTime() - startNano);

                success = true;
            }
        } finally {
            // Make sure we always set future notifications in case of errors
            if (!isDryRun && !success) {
                setFutureNotifications(account, futureAccountNotifications, internalCallContext);
            }

            if (isDryRun || success) {
                final DefaultInvoice refreshedInvoice = isDryRun ? invoice : new DefaultInvoice(invoiceDao.getById(invoice.getId(), internalCallContext));
                startNano = System.nanoTime();
                invoicePluginDispatcher.onSuccessCall(actualTargetDate, refreshedInvoice, accountInvoices.getInvoices(), isDryRun, isRescheduled, callContext, pluginProperties, internalCallContext);
                invoiceTimings.put(InvoiceTiming.PLUGINS_COMPLETION_CALL, System.nanoTime() - startNano);
            } else {
                startNano = System.nanoTime();
                invoicePluginDispatcher.onFailureCall(actualTargetDate, invoice, accountInvoices.getInvoices(), isDryRun, isRescheduled, callContext, pluginProperties, internalCallContext);
                invoiceTimings.put(InvoiceTiming.PLUGINS_COMPLETION_CALL, System.nanoTime() - startNano);
            }
        }

        return new InvoiceWithFutureNotifications(invoice, futureAccountNotifications);
    }

    private InvoiceWithMetadata generateKillBillInvoice(final ImmutableAccountData account, final LocalDate targetDate, final BillingEventSet billingEvents, final AccountInvoices accountInvoices, @Nullable final DryRunInfo dryRunInfo, final InternalCallContext context) throws InvoiceApiException {
        final UUID targetInvoiceId;
        // Filter out DRAFT invoices for computation  of existing items unless Account is in AUTO_INVOICING_REUSE_DRAFT
        if (billingEvents.isAccountAutoInvoiceReuseDraft()) {
            final Invoice existingDraft = Iterables.tryFind(accountInvoices.getInvoices(), new Predicate<Invoice>() {
                @Override
                public boolean apply(final Invoice input) {
                    return input.getStatus() == InvoiceStatus.DRAFT;
                }
            }).orNull();
            targetInvoiceId = existingDraft != null ? existingDraft.getId() : null;
        } else {
            targetInvoiceId = null;
        }

        return generator.generateInvoice(account, billingEvents, accountInvoices, targetInvoiceId, targetDate, account.getCurrency(), dryRunInfo, context);
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

    private void populateNextFutureNotificationDate(final InvoiceWithMetadata invoiceWithMetadata, final FutureAccountNotificationsBuilder notificationsBuilder) {
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
            for (final Entry<LocalDate, Set<UUID>> entry : notificationListForTrigger.entrySet()) {
                final LocalDate curDate = entry.getKey();
                final LocalDate curDryRunDate = context.toLocalDate(context.toUTCDateTime(curDate).minus(dryRunNotificationTime));
                Set<UUID> subscriptionsForDryRunDates = notificationListForDryRun.get(curDryRunDate);
                if (subscriptionsForDryRunDates == null) {
                    subscriptionsForDryRunDates = new HashSet<UUID>();
                    notificationListForDryRun.put(curDryRunDate, subscriptionsForDryRunDates);
                }
                subscriptionsForDryRunDates.addAll(entry.getValue());
            }

            final Map<UUID, DateTime> upcomingTransitionsForSubscriptions = getNextTransitionsForSubscriptions(billingEvents);

            for (final Entry<UUID, DateTime> entry : upcomingTransitionsForSubscriptions.entrySet()) {
                final LocalDate curDryRunDate = context.toLocalDate(entry.getValue().minus(dryRunNotificationTime));
                Set<UUID> subscriptionsForDryRunDates = notificationListForDryRun.get(curDryRunDate);
                if (subscriptionsForDryRunDates == null) {
                    subscriptionsForDryRunDates = new HashSet<UUID>();
                    notificationListForDryRun.put(curDryRunDate, subscriptionsForDryRunDates);
                }
                subscriptionsForDryRunDates.add(entry.getKey());
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

    private Set<UUID> getUniqueInvoiceIds(final Invoice invoice) {
        final Set<UUID> uniqueInvoiceIds = new TreeSet<UUID>();
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            uniqueInvoiceIds.add(invoiceItem.getInvoiceId());
        }
        return uniqueInvoiceIds;
    }

    private void logInvoiceWithItems(final ImmutableAccountData account, final Invoice invoice, final LocalDate targetDate, final Set<UUID> adjustedUniqueOtherInvoiceId, final boolean isRealInvoiceWithItems) {
        final StringBuilder tmp = new StringBuilder();
        if (isRealInvoiceWithItems) {
            tmp.append(String.format("Generated invoiceId='%s', numberOfItems='%d', accountId='%s', targetDate='%s':", invoice.getId(), invoice.getNumberOfItems(), account.getId(), targetDate));
        } else {
            final String adjustedInvoices = JOINER_COMMA.join(adjustedUniqueOtherInvoiceId.toArray(new UUID[adjustedUniqueOtherInvoiceId.size()]));
            tmp.append(String.format("Adjusting existing invoiceId='%s', numberOfItems='%d', accountId='%s', targetDate='%s':%n",
                                     adjustedInvoices, invoice.getNumberOfItems(), account.getId(), targetDate));
        }
        int n = 0;
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (n > MAX_NB_ITEMS_TO_PRINT) {
                // https://github.com/killbill/killbill/issues/1337
                tmp.append(String.format("%n\t... and %s more ...", invoice.getNumberOfItems() - n));
                break;
            }
            tmp.append(String.format("%n\t item = %s", item));
            n++;
        }
        log.info(tmp.toString());
    }

    private void setFutureNotifications(final ImmutableAccountData account,
                                        final FutureAccountNotifications futureAccountNotifications,
                                        final InternalCallContext context) {
        commitInvoiceAndSetFutureNotifications(account, null, null, ImmutableSet.of(), futureAccountNotifications, null, context);
    }

    private void commitInvoiceAndSetFutureNotifications(final ImmutableAccountData account,
                                                        @Nullable final InvoiceModelDao invoiceModelDao,
                                                        final BillingEventSet billingEvents,
                                                        final Set<InvoiceTrackingModelDao> trackingIds,
                                                        final FutureAccountNotifications futureAccountNotifications,
                                                        final ExistingInvoiceMetadata existingInvoiceMetadata,
                                                        final InternalCallContext context) {
        final boolean isThereAnyItemsLeft = invoiceModelDao != null && !invoiceModelDao.getInvoiceItems().isEmpty();
        if (isThereAnyItemsLeft) {
            invoiceDao.createInvoice(invoiceModelDao, billingEvents, trackingIds, futureAccountNotifications, existingInvoiceMetadata, context);
        } else {
            invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, context);
        }
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

    public void setChargedThroughDates(final Invoice invoice, final InternalCallContext context) throws InvoiceApiException {
        final Map<DateTime, List<UUID>> chargeThroughDates = InvoiceWithMetadata.computeChargedThroughDates(invoice, context);
        setChargedThroughDates(chargeThroughDates, context);
    }

    // TODO we should revisit this logic of swallowing the exception here -- especially in a use case where we use a catalog plugin
    private void setChargedThroughDatesNoExceptions(final Map<DateTime, List<UUID>> chargeThroughDates, final InternalCallContext context) {
        try {
            setChargedThroughDates(chargeThroughDates, context);
        } catch (final InvoiceApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void setChargedThroughDates(final Map<DateTime, List<UUID>> chargeThroughDates, final InternalCallContext context) throws InvoiceApiException {
        try {
            subscriptionApi.setChargedThroughDates(chargeThroughDates, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR, "Failed to set chargedThroughDates", e);
        }
    }

    private void postEvent(final BusInternalEvent event) {
        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
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
            final List<DateTime> tmp = new ArrayList<DateTime>();
            for (final Entry<UUID, DateTime> entry : nextScheduledSubscriptionsEventMap.entrySet()) {
                if (Iterables.contains(filteredSubscriptionIds, entry.getKey())) {
                    tmp.add(entry.getValue());
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

        final BigDecimal childInvoiceAmount = InvoiceCalculatorUtils.computeChildInvoiceAmount(childInvoice.getCurrency(), childInvoice.getInvoiceItems());
        InvoiceModelDao draftParentInvoice = invoiceDao.getParentDraftInvoice(childAccount.getParentAccountId(), parentContext);

        final String description = childAccount.getExternalKey().concat(" summary");
        if (draftParentInvoice != null) {
            for (final InvoiceItemModelDao item : draftParentInvoice.getInvoiceItems()) {
                if ((item.getChildAccountId() != null) && item.getChildAccountId().equals(childInvoice.getAccountId())) {
                    // update child item amount for existing parent invoice item
                    final BigDecimal newChildInvoiceAmount = childInvoiceAmount.add(item.getAmount());
                    log.info("Updating existing itemId='{}', oldAmount='{}', newAmount='{}' on existing DRAFT invoiceId='{}'", item.getId(), item.getAmount(), newChildInvoiceAmount, draftParentInvoice.getId());
                    invoiceDao.updateInvoiceItemAmount(item.getId(), newChildInvoiceAmount, parentContext);
                    return;
                }
            }

            // new item when the parent invoices does not have this child item yet
            final ParentInvoiceItem newParentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), context.getCreatedDate(), draftParentInvoice.getId(), childAccount.getParentAccountId(), childAccount.getId(), childInvoiceAmount, childAccount.getCurrency(), description);
            final InvoiceItemModelDao parentInvoiceItem = new InvoiceItemModelDao(newParentInvoiceItem);
            draftParentInvoice.addInvoiceItem(parentInvoiceItem);

            final List<InvoiceModelDao> invoices = new ArrayList<InvoiceModelDao>();
            invoices.add(draftParentInvoice);
            log.info("Adding new itemId='{}', amount='{}' on existing DRAFT invoiceId='{}'", parentInvoiceItem.getId(), childInvoiceAmount, draftParentInvoice.getId());
            invoiceDao.createInvoices(invoices, null, ImmutableSet.of(), parentContext);
        } else {
            if (shouldIgnoreChildInvoice(childInvoice, childInvoiceAmount)) {
                return;
            }

            final LocalDate invoiceDate = context.toLocalDate(context.getCreatedDate());
            draftParentInvoice = new InvoiceModelDao(childAccount.getParentAccountId(), invoiceDate, childAccount.getCurrency(), InvoiceStatus.DRAFT, true);
            final InvoiceItem parentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), context.getCreatedDate(), draftParentInvoice.getId(), childAccount.getParentAccountId(), childAccount.getId(), childInvoiceAmount, childAccount.getCurrency(), description);
            draftParentInvoice.addInvoiceItem(new InvoiceItemModelDao(parentInvoiceItem));

            log.info("Adding new itemId='{}', amount='{}' on new DRAFT invoiceId='{}'", parentInvoiceItem.getId(), childInvoiceAmount, draftParentInvoice.getId());
            invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(draftParentInvoice), null, ImmutableSet.of(), parentContext);
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
                for (final InvoiceItem item : childInvoice.getInvoiceItems()) {
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
            public boolean apply(final InvoiceItemModelDao input) {
                return input.getType().equals(InvoiceItemType.PARENT_SUMMARY)
                       && input.getChildAccountId().equals(childInvoiceModelDao.getAccountId());
            }
        });

        final Iterable<InvoiceItemModelDao> childAdjustments = Iterables.filter(childInvoiceModelDao.getInvoiceItems(), new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(final InvoiceItemModelDao input) {
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
            public int compare(final InvoiceItemModelDao o1, final InvoiceItemModelDao o2) {
                return o1.getCreatedDate().compareTo(o2.getCreatedDate());
            }
        });

        final BigDecimal childInvoiceAdjustmentAmount = lastChildInvoiceItemAdjustment.getAmount();

        if (parentInvoiceModelDao.getStatus().equals(InvoiceStatus.COMMITTED)) {
            final ItemAdjInvoiceItem adj = new ItemAdjInvoiceItem(UUIDs.randomUUID(),
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
            invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(parentInvoiceModelDao), null, ImmutableSet.of(), parentContext);
            return;
        }

        // update item amount
        final BigDecimal newParentInvoiceItemAmount = childInvoiceAdjustmentAmount.add(parentSummaryInvoiceItem.getAmount());
        invoiceDao.updateInvoiceItemAmount(parentSummaryInvoiceItem.getId(), newParentInvoiceItemAmount, parentContext);
    }

    private static class InvoiceWithFutureNotifications {

        private final Invoice invoice;
        private final FutureAccountNotifications notifications;

        public InvoiceWithFutureNotifications(final Invoice invoice, final FutureAccountNotifications notifications) {
            this.invoice = invoice;
            this.notifications = notifications;
        }

        public Invoice getInvoice() {
            return invoice;
        }

        public FutureAccountNotifications getNotifications() {
            return notifications;
        }
    }

}
