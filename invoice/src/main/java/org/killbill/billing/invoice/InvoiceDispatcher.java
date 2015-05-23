/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.SubscriptionNotification;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceNotificationInternalEvent;
import org.killbill.billing.invoice.api.user.DefaultNullInvoiceEvent;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.generator.BillingIntervalDetail;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.notification.NextBillingDateNotificationKey;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.InvoiceConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.billing.util.timezone.DateAndTimeZoneContext;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

public class InvoiceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);
    private static final int NB_LOCK_TRY = 5;

    private static final Ordering<DateTime> UPCOMING_NOTIFICATION_DATE_ORDERING = Ordering.natural();

    private static final NullDryRunArguments NULL_DRY_RUN_ARGUMENTS = new NullDryRunArguments();

    private final InvoiceGenerator generator;
    private final BillingInternalApi billingApi;
    private final AccountInternalApi accountApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceDao invoiceDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceNotifier invoiceNotifier;
    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final GlobalLocker locker;
    private final PersistentBus eventBus;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final InvoiceConfig invoiceConfig;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator,
                             final AccountInternalApi accountApi,
                             final BillingInternalApi billingApi,
                             final SubscriptionBaseInternalApi SubscriptionApi,
                             final InvoiceDao invoiceDao,
                             final InternalCallContextFactory internalCallContextFactory,
                             final InvoiceNotifier invoiceNotifier,
                             final InvoicePluginDispatcher invoicePluginDispatcher,
                             final GlobalLocker locker,
                             final PersistentBus eventBus,
                             final NotificationQueueService notificationQueueService,
                             final InvoiceConfig invoiceConfig,
                             final Clock clock) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.subscriptionApi = SubscriptionApi;
        this.accountApi = accountApi;
        this.invoiceDao = invoiceDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceNotifier = invoiceNotifier;
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.locker = locker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.invoiceConfig = invoiceConfig;
    }

    public void processSubscriptionForInvoiceGeneration(final EffectiveSubscriptionInternalEvent transition,
                                                        final InternalCallContext context) throws InvoiceApiException {
        final UUID subscriptionId = transition.getSubscriptionId();
        final DateTime targetDate = transition.getEffectiveTransitionTime();
        processSubscriptionForInvoiceGeneration(subscriptionId, targetDate, context);
    }

    public void processSubscriptionForInvoiceGeneration(final UUID subscriptionId, final DateTime targetDate, final InternalCallContext context) throws InvoiceApiException {
        processSubscriptionInternal(subscriptionId, targetDate, false, context);
    }

    public void processSubscriptionForInvoiceNotification(final UUID subscriptionId, final DateTime targetDate, final InternalCallContext context) throws InvoiceApiException {
        final Invoice dryRunInvoice = processSubscriptionInternal(subscriptionId, targetDate, true, context);
        if (dryRunInvoice != null && dryRunInvoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            final InvoiceNotificationInternalEvent event = new DefaultInvoiceNotificationInternalEvent(dryRunInvoice.getAccountId(), dryRunInvoice.getBalance(), dryRunInvoice.getCurrency(),
                                                                                                       targetDate, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
            try {
                eventBus.post(event);
            } catch (EventBusException e) {
                log.error("Failed to post event " + event, e);
            }
        }
    }

    private Invoice processSubscriptionInternal(final UUID subscriptionId, final DateTime targetDate, final boolean dryRunForNotification, final InternalCallContext context) throws InvoiceApiException {
        try {
            if (subscriptionId == null) {
                log.error("Failed handling SubscriptionBase change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
                return null;
            }
            final UUID accountId = subscriptionApi.getAccountIdFromSubscriptionId(subscriptionId, context);
            final DryRunArguments dryRunArguments = dryRunForNotification ? NULL_DRY_RUN_ARGUMENTS : null;

            return processAccount(accountId, targetDate, dryRunArguments, context);
        } catch (final SubscriptionBaseApiException e) {
            log.error("Failed handling SubscriptionBase change.",
                      new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return null;
        }
    }

    public Invoice processAccount(final UUID accountId, final DateTime targetDate,
                                  @Nullable final DryRunArguments dryRunArguments, final InternalCallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), NB_LOCK_TRY);

            return processAccountWithLock(accountId, targetDate, dryRunArguments, context);
        } catch (final LockFailedException e) {
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

    private Invoice processAccountWithLock(final UUID accountId, @Nullable final DateTime inputTargetDateTime,
                                           @Nullable final DryRunArguments dryRunArguments, final InternalCallContext context) throws InvoiceApiException {

        final boolean isDryRun = dryRunArguments != null;
        // inputTargetDateTime is only allowed in dryRun mode to have the system compute it
        Preconditions.checkArgument(inputTargetDateTime != null || isDryRun, "inputTargetDateTime is required in non dryRun mode");
        try {
            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, dryRunArguments, context);

            final List<DateTime> candidateDateTimes = (inputTargetDateTime != null) ? ImmutableList.of(inputTargetDateTime) : getUpcomingInvoiceCandidateDates(context);
            for (final DateTime curTargetDateTime : candidateDateTimes) {
                final Invoice invoice = processAccountWithLockAndInputTargetDate(accountId, curTargetDateTime, billingEvents, isDryRun, context);
                if (invoice != null) {
                    return invoice;
                }
            }
            return null;
        } catch (CatalogApiException e) {
            log.error("Failed handling SubscriptionBase change.", e);
            return null;
        }
    }

    private Invoice processAccountWithLockAndInputTargetDate(final UUID accountId, final DateTime targetDateTime,
                                                             final BillingEventSet billingEvents, final boolean isDryRun, final InternalCallContext context) throws InvoiceApiException {
        try {
            final Account account = accountApi.getAccountById(accountId, context);
            final DateAndTimeZoneContext dateAndTimeZoneContext = billingEvents.iterator().hasNext() ?
                                                                  new DateAndTimeZoneContext(billingEvents.iterator().next().getEffectiveDate(), account.getTimeZone(), clock) :
                                                                  null;

            final List<Invoice> invoices = billingEvents.isAccountAutoInvoiceOff() ?
                                           ImmutableList.<Invoice>of() :
                                           ImmutableList.<Invoice>copyOf(Collections2.transform(invoiceDao.getInvoicesByAccount(context),
                                                                                                new Function<InvoiceModelDao, Invoice>() {
                                                                                                    @Override
                                                                                                    public Invoice apply(final InvoiceModelDao input) {
                                                                                                        return new DefaultInvoice(input);
                                                                                                    }
                                                                                                }));

            final Currency targetCurrency = account.getCurrency();

            final LocalDate targetDate = (dateAndTimeZoneContext != null && targetDateTime != null) ? dateAndTimeZoneContext.computeTargetDate(targetDateTime) : null;
            final Invoice invoice = targetDate != null ? generator.generateInvoice(account, billingEvents, invoices, targetDate, targetCurrency, context) : null;
            //
            // If invoice comes back null, there is nothing new to generate, we can bail early
            //
            if (invoice == null) {
                log.info("Generated null invoice for accountId {} and targetDate {} (targetDateTime {})", new Object[]{accountId, targetDate, targetDateTime});
                if (!isDryRun) {
                    final BusInternalEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(),
                                                                               context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                    postEvent(event, accountId, context);
                }
                return invoice;
            }

            // Generate missing credit (> 0 for generation and < 0 for use) prior we call the plugin
            final InvoiceItem cbaItem = computeCBAOnExistingInvoice(invoice, context);
            if (cbaItem != null) {
                invoice.addInvoiceItem(cbaItem);
            }

            //
            // Ask external invoice plugins if additional items (tax, etc) shall be added to the invoice
            //
            final CallContext callContext = buildCallContext(context);
            invoice.addInvoiceItems(invoicePluginDispatcher.getAdditionalInvoiceItems(invoice, callContext));
            if (!isDryRun) {
                commitInvoiceStateAndNotifyAccountIfConfigured(account, invoice, billingEvents, dateAndTimeZoneContext, targetDate, context);
            }
            return invoice;
        } catch (final AccountApiException e) {
            log.error("Failed handling SubscriptionBase change.", e);
            return null;
        } catch (SubscriptionBaseApiException e) {
            log.error("Failed handling SubscriptionBase change.", e);
            return null;
        }
    }

    private void commitInvoiceStateAndNotifyAccountIfConfigured(final Account account, final Invoice invoice, final BillingEventSet billingEvents, final DateAndTimeZoneContext dateAndTimeZoneContext, final LocalDate targetDate, final InternalCallContext context) throws SubscriptionBaseApiException, InvoiceApiException {
        boolean isRealInvoiceWithNonEmptyItems = false;
        // Extract the set of invoiceId for which we see items that don't belong to current generated invoice
        final Set<UUID> adjustedUniqueOtherInvoiceId = new TreeSet<UUID>();
        adjustedUniqueOtherInvoiceId.addAll(Collections2.transform(invoice.getInvoiceItems(), new Function<InvoiceItem, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final InvoiceItem input) {
                return input.getInvoiceId();
            }
        }));
        boolean isRealInvoiceWithItems = adjustedUniqueOtherInvoiceId.remove(invoice.getId());
        if (isRealInvoiceWithItems) {
            log.info("Generated invoice {} with {} items for accountId {} and targetDate {}", new Object[]{invoice.getId(), invoice.getNumberOfItems(), account.getId(), targetDate});
        } else {
            final Joiner joiner = Joiner.on(",");
            final String adjustedInvoices = joiner.join(adjustedUniqueOtherInvoiceId.toArray(new UUID[adjustedUniqueOtherInvoiceId.size()]));
            log.info("Adjusting existing invoices {} with {} items for accountId {} and targetDate {})", new Object[]{adjustedInvoices, invoice.getNumberOfItems(),
                                                                                                                                         account.getId(), targetDate});
        }

        // Transformation to Invoice -> InvoiceModelDao
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        final Iterable<InvoiceItemModelDao> invoiceItemModelDaos = Iterables.transform(invoice.getInvoiceItems(),
                                                                                       new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                                                           @Override
                                                                                           public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                                               return new InvoiceItemModelDao(input);
                                                                                           }
                                                                                       });
        final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(invoiceItemModelDaos, billingEvents, dateAndTimeZoneContext, context);

        // We filter any zero amount for USAGE items prior we generate the invoice, which may leave us with an invoice with no items;
        // we recompute the isRealInvoiceWithItems flag based on what is left (the call to invoice is still necessary to set the future notifications).
        final Iterable<InvoiceItemModelDao> filteredInvoiceItemModelDaos = Iterables.filter(invoiceItemModelDaos, new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(@Nullable final InvoiceItemModelDao input) {
                return (input.getType() != InvoiceItemType.USAGE || input.getAmount().compareTo(BigDecimal.ZERO) != 0);
            }
        });

        final boolean isThereAnyItemsLeft = filteredInvoiceItemModelDaos.iterator().hasNext();
        isRealInvoiceWithNonEmptyItems = isThereAnyItemsLeft ? isRealInvoiceWithItems : false;

        if (isThereAnyItemsLeft) {
            invoiceDao.createInvoice(invoiceModelDao, ImmutableList.copyOf(filteredInvoiceItemModelDaos), isRealInvoiceWithItems, futureAccountNotifications, context);
        } else {
            invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, context);
        }

        final List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
        final List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
        setChargedThroughDates(dateAndTimeZoneContext, fixedPriceInvoiceItems, recurringInvoiceItems, context);

        final List<InvoiceInternalEvent> events = new ArrayList<InvoiceInternalEvent>();
        if (isRealInvoiceWithNonEmptyItems) {
            events.add(new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                       invoice.getBalance(), invoice.getCurrency(),
                                                       context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()));
        }
        for (final UUID cur : adjustedUniqueOtherInvoiceId) {
            final InvoiceAdjustmentInternalEvent event = new DefaultInvoiceAdjustmentEvent(cur, invoice.getAccountId(),
                                                                                           context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
            events.add(event);
        }

        for (final InvoiceInternalEvent event : events) {
            postEvent(event, account.getId(), context);
        }

        if (account.isNotifiedForInvoices() && isRealInvoiceWithNonEmptyItems) {
            // Need to re-hydrate the invoice object to get the invoice number (record id)
            // API_FIX InvoiceNotifier public API?
            invoiceNotifier.notify(account, new DefaultInvoice(invoiceDao.getById(invoice.getId(), context)), buildTenantContext(context));
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

    @VisibleForTesting
    FutureAccountNotifications createNextFutureNotificationDate(final Iterable<InvoiceItemModelDao> invoiceItems, final BillingEventSet billingEvents, final DateAndTimeZoneContext dateAndTimeZoneContext, final InternalCallContext context) {

        final Map<UUID, List<SubscriptionNotification>> result = new HashMap<UUID, List<SubscriptionNotification>>();

        final Map<String, LocalDate> perSubscriptionUsage = new HashMap<String, LocalDate>();

        // For each subscription that has a positive (amount) recurring item, create the date
        // at which we should be called back for next invoice.
        //
        for (final InvoiceItemModelDao item : invoiceItems) {

            List<SubscriptionNotification> perSubscriptionCallback = result.get(item.getSubscriptionId());
            if (perSubscriptionCallback == null && (item.getType() == InvoiceItemType.RECURRING || item.getType() == InvoiceItemType.USAGE)) {
                perSubscriptionCallback = new ArrayList<SubscriptionNotification>();
                result.put(item.getSubscriptionId(), perSubscriptionCallback);
            }

            switch (item.getType()) {
                case RECURRING:
                    if ((item.getEndDate() != null) &&
                        (item.getAmount() == null ||
                         item.getAmount().compareTo(BigDecimal.ZERO) >= 0)) {
                        perSubscriptionCallback.add(new SubscriptionNotification(dateAndTimeZoneContext.computeUTCDateTimeFromLocalDate(item.getEndDate()), true));
                    }
                    break;

                case USAGE:
                    final String key = item.getSubscriptionId().toString() + ":" + item.getUsageName();
                    final LocalDate perSubscriptionUsageRecurringDate = perSubscriptionUsage.get(key);
                    if (perSubscriptionUsageRecurringDate == null || perSubscriptionUsageRecurringDate.compareTo(item.getEndDate()) < 0) {
                        perSubscriptionUsage.put(key, item.getEndDate());
                    }
                    break;

                default:
                    // Ignore
            }
        }

        for (final String key : perSubscriptionUsage.keySet()) {
            final String[] parts = key.split(":");
            final UUID subscriptionId = UUID.fromString(parts[0]);

            final List<SubscriptionNotification> perSubscriptionCallback = result.get(subscriptionId);
            final String usageName = parts[1];
            final LocalDate endDate = perSubscriptionUsage.get(key);

            final DateTime subscriptionUsageCallbackDate = getNextUsageBillingDate(subscriptionId, usageName, endDate, dateAndTimeZoneContext, billingEvents);
            perSubscriptionCallback.add(new SubscriptionNotification(subscriptionUsageCallbackDate, true));
        }

        // If dryRunNotification is enabled we also need to fetch the upcoming PHASE dates (we add SubscriptionNotification with isForInvoiceNotificationTrigger = false)
        final boolean isInvoiceNotificationEnabled = invoiceConfig.getDryRunNotificationSchedule().getMillis() > 0;
        if (isInvoiceNotificationEnabled) {
            final Map<UUID, DateTime> upcomingPhasesForSubscriptions = subscriptionApi.getNextFutureEventForSubscriptions(SubscriptionBaseTransitionType.PHASE, context);
            for (UUID cur : upcomingPhasesForSubscriptions.keySet()) {
                final DateTime curDate = upcomingPhasesForSubscriptions.get(cur);
                List<SubscriptionNotification> resultValue = result.get(cur);
                if (resultValue == null) {
                    resultValue = new ArrayList<SubscriptionNotification>();
                }
                resultValue.add(new SubscriptionNotification(curDate, false));
                result.put(cur, resultValue);
            }
        }
        return new FutureAccountNotifications(dateAndTimeZoneContext, result);
    }

    private DateTime getNextUsageBillingDate(final UUID subscriptionId, final String usageName, final LocalDate chargedThroughDate, final DateAndTimeZoneContext dateAndTimeZoneContext, final BillingEventSet billingEvents) {

        final Usage usage = billingEvents.getUsages().get(usageName);
        final BillingEvent billingEventSubscription = Iterables.tryFind(billingEvents, new Predicate<BillingEvent>() {
            @Override
            public boolean apply(@Nullable final BillingEvent input) {
                return input.getSubscription().getId().equals(subscriptionId);
            }
        }).orNull();

        final LocalDate nextCallbackUsageDate = (usage.getBillingMode() == BillingMode.IN_ARREAR) ? BillingIntervalDetail.alignProposedBillCycleDate(chargedThroughDate.plusMonths(usage.getBillingPeriod().getNumberOfMonths()), billingEventSubscription.getBillCycleDayLocal()) : chargedThroughDate;
        return dateAndTimeZoneContext.computeUTCDateTimeFromLocalDate(nextCallbackUsageDate);
    }

    private void setChargedThroughDates(final DateAndTimeZoneContext dateAndTimeZoneContext,
                                        final Collection<InvoiceItem> fixedPriceItems,
                                        final Collection<InvoiceItem> recurringItems,
                                        final InternalCallContext context) throws SubscriptionBaseApiException {
        final Map<UUID, DateTime> chargeThroughDates = new HashMap<UUID, DateTime>();
        addInvoiceItemsToChargeThroughDates(dateAndTimeZoneContext, chargeThroughDates, fixedPriceItems);
        addInvoiceItemsToChargeThroughDates(dateAndTimeZoneContext, chargeThroughDates, recurringItems);

        for (final UUID subscriptionId : chargeThroughDates.keySet()) {
            if (subscriptionId != null) {
                final DateTime chargeThroughDate = chargeThroughDates.get(subscriptionId);
                subscriptionApi.setChargedThroughDate(subscriptionId, chargeThroughDate, context);
            }
        }
    }

    private void postEvent(final BusInternalEvent event, final UUID accountId, final InternalCallContext context) {
        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.error(String.format("Failed to post event %s for account %s", event.getBusEventType(), accountId), e);
        }
    }

    private void addInvoiceItemsToChargeThroughDates(final DateAndTimeZoneContext dateAndTimeZoneContext,
                                                     final Map<UUID, DateTime> chargeThroughDates,
                                                     final Collection<InvoiceItem> items) {

        for (final InvoiceItem item : items) {
            final UUID subscriptionId = item.getSubscriptionId();
            final LocalDate endDate = (item.getEndDate() != null) ? item.getEndDate() : item.getStartDate();

            final DateTime proposedChargedThroughDate = dateAndTimeZoneContext.computeUTCDateTimeFromLocalDate(endDate);
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

        private final DateAndTimeZoneContext accountDateAndTimeZoneContext;
        private final Map<UUID, List<SubscriptionNotification>> notifications;

        public FutureAccountNotifications(final DateAndTimeZoneContext accountDateAndTimeZoneContext, final Map<UUID, List<SubscriptionNotification>> notifications) {
            this.accountDateAndTimeZoneContext = accountDateAndTimeZoneContext;
            this.notifications = notifications;
        }

        public DateAndTimeZoneContext getAccountDateAndTimeZoneContext() {
            return accountDateAndTimeZoneContext;
        }

        public Map<UUID, List<SubscriptionNotification>> getNotifications() {
            return notifications;
        }

        public static class SubscriptionNotification {

            private final DateTime effectiveDate;
            private final boolean isForNotificationTrigger;


            public SubscriptionNotification(final DateTime effectiveDate, final boolean isForNotificationTrigger) {
                this.effectiveDate = effectiveDate;
                this.isForNotificationTrigger = isForNotificationTrigger;
            }

            public DateTime getEffectiveDate() {
                return effectiveDate;
            }

            public boolean isForInvoiceNotificationTrigger() {
                return isForNotificationTrigger;
            }
        }
    }

    private List<DateTime> getUpcomingInvoiceCandidateDates(final InternalCallContext internalCallContext) {
        final Iterable<DateTime> nextScheduledInvoiceDates = getNextScheduledInvoiceEffectiveDate(internalCallContext);
        final Iterable<DateTime> nextScheduledSubscriptionsEventDates = subscriptionApi.getFutureNotificationsForAccount(internalCallContext);
        Iterables.concat(nextScheduledInvoiceDates, nextScheduledSubscriptionsEventDates);
        return UPCOMING_NOTIFICATION_DATE_ORDERING.sortedCopy(Iterables.concat(nextScheduledInvoiceDates, nextScheduledSubscriptionsEventDates));
    }

    private Iterable<DateTime> getNextScheduledInvoiceEffectiveDate(final InternalCallContext internalCallContext) {
        try {
            final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                                                      DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
            final List<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

            final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> filtered = Iterables.filter(futureNotifications, new Predicate<NotificationEventWithMetadata<NextBillingDateNotificationKey>>() {
                @Override
                public boolean apply(@Nullable final NotificationEventWithMetadata<NextBillingDateNotificationKey> input) {

                    final boolean isEventDryRunForNotifications = input.getEvent().isDryRunForInvoiceNotification() != null ?
                                                                  input.getEvent().isDryRunForInvoiceNotification() : false;
                    return !isEventDryRunForNotifications;
                }
            });

            return Iterables.transform(filtered, new Function<NotificationEventWithMetadata<NextBillingDateNotificationKey>, DateTime>() {
                @Nullable
                @Override
                public DateTime apply(@Nullable final NotificationEventWithMetadata<NextBillingDateNotificationKey> input) {
                    return input.getEffectiveDate();
                }
            });
        } catch (final NoSuchNotificationQueue noSuchNotificationQueue) {
            throw new IllegalStateException(noSuchNotificationQueue);
        }
    }

    private final static class NullDryRunArguments implements DryRunArguments {

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
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
        public DateTime getEffectiveDate() {
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
        public List<PlanPhasePriceOverride> getPlanPhasePriceoverrides() {
            return null;
        }
    }
}
