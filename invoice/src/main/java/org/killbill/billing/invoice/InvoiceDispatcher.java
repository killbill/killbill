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

package org.killbill.billing.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.SubscriptionNotification;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceNotificationInternalEvent;
import org.killbill.billing.invoice.api.user.DefaultNullInvoiceEvent;
import org.killbill.billing.invoice.calculator.InvoiceCalculatorUtils;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDaoHelper;
import org.killbill.billing.invoice.dao.InvoiceParentChildModelDao;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates.UsageDef;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.ParentInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.notification.NextBillingDateNotificationKey;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.globallocker.LockerType;
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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
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
        final LocalDate targetDate = context.toLocalDate(transition.getEffectiveTransitionTime());
        processSubscriptionForInvoiceGeneration(subscriptionId, targetDate, context);
    }

    public void processSubscriptionForInvoiceGeneration(final UUID subscriptionId, final LocalDate targetDate, final InternalCallContext context) throws InvoiceApiException {
        processSubscriptionInternal(subscriptionId, targetDate, false, context);
    }

    public void processSubscriptionForInvoiceNotification(final UUID subscriptionId, final LocalDate targetDate, final InternalCallContext context) throws InvoiceApiException {
        final Invoice dryRunInvoice = processSubscriptionInternal(subscriptionId, targetDate, true, context);
        if (dryRunInvoice != null && dryRunInvoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            final InvoiceNotificationInternalEvent event = new DefaultInvoiceNotificationInternalEvent(dryRunInvoice.getAccountId(), dryRunInvoice.getBalance(), dryRunInvoice.getCurrency(),
                                                                                                       context.toUTCDateTime(targetDate), context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
            try {
                eventBus.post(event);
            } catch (EventBusException e) {
                log.warn("Failed to post event {}", event, e);
            }
        }
    }

    private Invoice processSubscriptionInternal(final UUID subscriptionId, final LocalDate targetDate, final boolean dryRunForNotification, final InternalCallContext context) throws InvoiceApiException {
        try {
            if (subscriptionId == null) {
                log.warn("Failed handling SubscriptionBase change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
                return null;
            }
            final UUID accountId = subscriptionApi.getAccountIdFromSubscriptionId(subscriptionId, context);
            final DryRunArguments dryRunArguments = dryRunForNotification ? TARGET_DATE_DRY_RUN_ARGUMENTS : null;

            return processAccount(accountId, targetDate, dryRunArguments, context);
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed handling SubscriptionBase change.",
                      new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return null;
        }
    }

    public Invoice processAccount(final UUID accountId, @Nullable final LocalDate targetDate,
                                  @Nullable final DryRunArguments dryRunArguments, final InternalCallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), accountId.toString(), invoiceConfig.getMaxGlobalLockRetries());

            return processAccountWithLock(accountId, targetDate, dryRunArguments, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process invoice for accountId='{}', targetDate='{}'", accountId.toString(), targetDate, e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private Invoice processAccountWithLock(final UUID accountId, @Nullable final LocalDate inputTargetDateMaybeNull,
                                           @Nullable final DryRunArguments dryRunArguments, final InternalCallContext context) throws InvoiceApiException {
        final boolean isDryRun = dryRunArguments != null;
        final boolean upcomingInvoiceDryRun = isDryRun && DryRunType.UPCOMING_INVOICE.equals(dryRunArguments.getDryRunType());

        LocalDate inputTargetDate = inputTargetDateMaybeNull;
        // A null inputTargetDate is only allowed in dryRun mode to have the system compute it
        if (inputTargetDate == null && !upcomingInvoiceDryRun) {
            inputTargetDate = clock.getUTCToday();
        }
        Preconditions.checkArgument(inputTargetDate != null || upcomingInvoiceDryRun, "inputTargetDate is required in non dryRun mode");

        try {
            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, dryRunArguments, context);
            if (billingEvents.isEmpty()) {
                return null;
            }
            final Iterable<UUID> filteredSubscriptionIdsForDryRun = getFilteredSubscriptionIdsForDryRun(dryRunArguments, billingEvents);
            final List<LocalDate> candidateTargetDates = (inputTargetDate != null) ?
                                                         ImmutableList.<LocalDate>of(inputTargetDate) :
                                                         getUpcomingInvoiceCandidateDates(filteredSubscriptionIdsForDryRun, context);
            for (final LocalDate curTargetDate : candidateTargetDates) {
                final Invoice invoice = processAccountWithLockAndInputTargetDate(accountId, curTargetDate, billingEvents, isDryRun, context);
                if (invoice != null) {
                    filterInvoiceItemsForDryRun(filteredSubscriptionIdsForDryRun, invoice);
                    return invoice;
                }
            }
            return null;
        } catch (final CatalogApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        } catch (final AccountApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        } catch (final SubscriptionBaseApiException e) {
            log.warn("Failed to retrieve BillingEvents for accountId='{}', dryRunArguments='{}'", accountId, dryRunArguments, e);
            return null;
        }
    }

    private void filterInvoiceItemsForDryRun(final Iterable<UUID> filteredSubscriptionIdsForDryRun, final Invoice invoice) {
        if (!filteredSubscriptionIdsForDryRun.iterator().hasNext()) {
            return;
        }

        final Iterator<InvoiceItem> it = invoice.getInvoiceItems().iterator();
        while (it.hasNext()) {
            final InvoiceItem cur = it.next();
            if (!Iterables.contains(filteredSubscriptionIdsForDryRun, cur.getSubscriptionId())) {
                it.remove();
            }
        }
    }

    private Iterable<UUID> getFilteredSubscriptionIdsForDryRun(@Nullable final DryRunArguments dryRunArguments, final BillingEventSet billingEvents) {
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
                return input.getSubscription().getBundleId().equals(dryRunArguments.getBundleId());
            }
        }), new Function<BillingEvent, UUID>() {
            @Override
            public UUID apply(final BillingEvent input) {
                return input.getSubscription().getId();
            }
        });
    }

    private Invoice processAccountWithLockAndInputTargetDate(final UUID accountId, final LocalDate targetDate,
                                                             final BillingEventSet billingEvents, final boolean isDryRun, final InternalCallContext context) throws InvoiceApiException {
        try {
            final ImmutableAccountData account = accountApi.getImmutableAccountDataById(accountId, context);

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
            final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, billingEvents, invoices, targetDate, targetCurrency, context);
            final Invoice invoice = invoiceWithMetadata.getInvoice();

            // Compute future notifications
            final FutureAccountNotifications futureAccountNotifications = createNextFutureNotificationDate(invoiceWithMetadata, context);

            //

            // If invoice comes back null, there is nothing new to generate, we can bail early
            //
            if (invoice == null) {
                if (isDryRun) {
                    log.info("Generated null dryRun invoice for accountId='{}', targetDate='{}'", accountId, targetDate);
                } else {
                    log.info("Generated null invoice for accountId='{}', targetDate='{}'", accountId, targetDate);

                    final BusInternalEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(),
                                                                               context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());

                    commitInvoiceAndSetFutureNotifications(account, null, ImmutableList.<InvoiceItemModelDao>of(), futureAccountNotifications, false, context);
                    postEvent(event);
                }
                return null;
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
            invoice.addInvoiceItems(invoicePluginDispatcher.getAdditionalInvoiceItems(invoice, isDryRun, callContext));

            if (!isDryRun) {

                // Compute whether this is a new invoice object (or just some adjustments on an existing invoice), and extract invoiceIds for later use
                final Set<UUID> uniqueInvoiceIds = getUniqueInvoiceIds(invoice);
                final boolean isRealInvoiceWithItems = uniqueInvoiceIds.remove(invoice.getId());
                final Set<UUID> adjustedUniqueOtherInvoiceId = uniqueInvoiceIds;

                logInvoiceWithItems(account, invoice, targetDate, adjustedUniqueOtherInvoiceId, isRealInvoiceWithItems);

                // Transformation to Invoice -> InvoiceModelDao
                final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
                final Iterable<InvoiceItemModelDao> invoiceItemModelDaos = transformToInvoiceModelDao(invoice.getInvoiceItems());

                // Commit invoice on disk
                final boolean isThereAnyItemsLeft = commitInvoiceAndSetFutureNotifications(account, invoiceModelDao, invoiceItemModelDaos, futureAccountNotifications, isRealInvoiceWithItems, context);

                final boolean isRealInvoiceWithNonEmptyItems = isThereAnyItemsLeft ? isRealInvoiceWithItems : false;

                setChargedThroughDates(invoice.getInvoiceItems(FixedPriceInvoiceItem.class), invoice.getInvoiceItems(RecurringInvoiceItem.class), context);

                if (InvoiceStatus.COMMITTED.equals(invoice.getStatus())) {
                    // TODO we should send bus events when we commit the invoice on disk in commitInvoice
                    postEvents(account, invoice, adjustedUniqueOtherInvoiceId, isRealInvoiceWithNonEmptyItems, context);
                    notifyAccountIfEnabled(account, invoice, isRealInvoiceWithNonEmptyItems, context);
                }

            }
            return invoice;
        } catch (final AccountApiException e) {
            log.error("Failed handling SubscriptionBase change.", e);
            return null;
        } catch (final SubscriptionBaseApiException e) {
            log.error("Failed handling SubscriptionBase change.", e);
            return null;
        }
    }

    private FutureAccountNotifications createNextFutureNotificationDate(final InvoiceWithMetadata invoiceWithMetadata, final InternalCallContext context) {
        final Map<UUID, List<SubscriptionNotification>> result = new HashMap<UUID, List<SubscriptionNotification>>();

        for (final UUID subscriptionId : invoiceWithMetadata.getPerSubscriptionFutureNotificationDates().keySet()) {

            final List<SubscriptionNotification> perSubscriptionNotifications = new ArrayList<SubscriptionNotification>();

            final SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = invoiceWithMetadata.getPerSubscriptionFutureNotificationDates().get(subscriptionId);
            // Add next recurring date if any
            if (subscriptionFutureNotificationDates.getNextRecurringDate() != null) {
                perSubscriptionNotifications.add(new SubscriptionNotification(context.toUTCDateTime(subscriptionFutureNotificationDates.getNextRecurringDate()), true));
            }
            // Add next usage dates if any
            if (subscriptionFutureNotificationDates.getNextUsageDates() != null) {
                for (final UsageDef usageDef : subscriptionFutureNotificationDates.getNextUsageDates().keySet()) {
                    final LocalDate nextNotificationDateForUsage = subscriptionFutureNotificationDates.getNextUsageDates().get(usageDef);
                    final DateTime subscriptionUsageCallbackDate = nextNotificationDateForUsage != null ? context.toUTCDateTime(nextNotificationDateForUsage) : null;
                    perSubscriptionNotifications.add(new SubscriptionNotification(subscriptionUsageCallbackDate, true));
                }
            }
            if (!perSubscriptionNotifications.isEmpty()) {
                result.put(subscriptionId, perSubscriptionNotifications);
            }
        }

        // If dryRunNotification is enabled we also need to fetch the upcoming PHASE dates (we add SubscriptionNotification with isForInvoiceNotificationTrigger = false)
        final boolean isInvoiceNotificationEnabled = invoiceConfig.getDryRunNotificationSchedule(context).getMillis() > 0;
        if (isInvoiceNotificationEnabled) {
            final Map<UUID, DateTime> upcomingPhasesForSubscriptions = subscriptionApi.getNextFutureEventForSubscriptions(SubscriptionBaseTransitionType.PHASE, context);
            for (final UUID cur : upcomingPhasesForSubscriptions.keySet()) {
                final DateTime curDate = upcomingPhasesForSubscriptions.get(cur);
                List<SubscriptionNotification> resultValue = result.get(cur);
                if (resultValue == null) {
                    resultValue = new ArrayList<SubscriptionNotification>();
                }
                resultValue.add(new SubscriptionNotification(curDate, false));
                result.put(cur, resultValue);
            }
        }
        return new FutureAccountNotifications(result);
    }

    private Iterable<InvoiceItemModelDao> transformToInvoiceModelDao(final List<InvoiceItem> invoiceItems) {
        return Iterables.transform(invoiceItems,
                                   new Function<InvoiceItem, InvoiceItemModelDao>() {
                                       @Override
                                       public InvoiceItemModelDao apply(final InvoiceItem input) {
                                           return new InvoiceItemModelDao(input);
                                       }
                                   });
    }

    private Set<UUID> getUniqueInvoiceIds(final Invoice invoice) {
        final Set<UUID> uniqueInvoiceIds = new TreeSet<UUID>();
        uniqueInvoiceIds.addAll(Collections2.transform(invoice.getInvoiceItems(), new Function<InvoiceItem, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final InvoiceItem input) {
                return input.getInvoiceId();
            }
        }));
        return uniqueInvoiceIds;
    }

    private void logInvoiceWithItems(final ImmutableAccountData account, final Invoice invoice, final LocalDate targetDate, final Set<UUID> adjustedUniqueOtherInvoiceId, final boolean isRealInvoiceWithItems) {
        final StringBuilder tmp = new StringBuilder();
        if (isRealInvoiceWithItems) {
            tmp.append(String.format("Generated invoiceId='%s', numberOfItems='%d', accountId='%s', targetDate='%s':", invoice.getId(), invoice.getNumberOfItems(), account.getId(), targetDate));
        } else {
            final String adjustedInvoices = JOINER_COMMA.join(adjustedUniqueOtherInvoiceId.toArray(new UUID[adjustedUniqueOtherInvoiceId.size()]));
            tmp.append(String.format("Adjusting existing invoiceId='%s', numberOfItems='%d', accountId='%s', targetDate='%s':\n",
                                     adjustedInvoices, invoice.getNumberOfItems(), account.getId(), targetDate));
        }
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            tmp.append(String.format("\n\t item = %s", item));
        }
        log.info(tmp.toString());
    }

    private boolean commitInvoiceAndSetFutureNotifications(final ImmutableAccountData account, final InvoiceModelDao invoiceModelDao,
                                                           final Iterable<InvoiceItemModelDao> invoiceItemModelDaos,
                                                           final FutureAccountNotifications futureAccountNotifications,
                                                           final boolean isRealInvoiceWithItems, final InternalCallContext context) throws SubscriptionBaseApiException, InvoiceApiException {
        // We filter any zero amount for USAGE items prior we generate the invoice, which may leave us with an invoice with no items;
        // we recompute the isRealInvoiceWithItems flag based on what is left (the call to invoice is still necessary to set the future notifications).
        final Iterable<InvoiceItemModelDao> filteredInvoiceItemModelDaos = Iterables.filter(invoiceItemModelDaos, new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(@Nullable final InvoiceItemModelDao input) {
                return (input.getType() != InvoiceItemType.USAGE || input.getAmount().compareTo(BigDecimal.ZERO) != 0);
            }
        });

        final boolean isThereAnyItemsLeft = filteredInvoiceItemModelDaos.iterator().hasNext();
        if (isThereAnyItemsLeft) {
            invoiceDao.createInvoice(invoiceModelDao, ImmutableList.copyOf(filteredInvoiceItemModelDaos), isRealInvoiceWithItems, futureAccountNotifications, context);
        } else {
            invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, context);
        }
        return isThereAnyItemsLeft;
    }

    private void postEvents(final ImmutableAccountData account, final Invoice invoice, final Set<UUID> adjustedUniqueOtherInvoiceId, final boolean isRealInvoiceWithNonEmptyItems, final InternalCallContext context) {

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
            postEvent(event);
        }
    }

    private void notifyAccountIfEnabled(final ImmutableAccountData account, final Invoice invoice, final boolean isRealInvoiceWithNonEmptyItems, final InternalCallContext context) throws InvoiceApiException, AccountApiException {
        // Ideally we would retrieve the cached version, all the invoice code has been modified to only use ImmutableAccountData, except for the
        // isNotifiedForInvoice piece that should probably live outside of invoice code anyways... (see https://github.com/killbill/killbill-email-notifications-plugin)
        final Account fullAccount = accountApi.getAccountById(account.getId(), context);

        if (fullAccount.isNotifiedForInvoices() && isRealInvoiceWithNonEmptyItems) {
            // Need to re-hydrate the invoice object to get the invoice number (record id)
            // API_FIX InvoiceNotifier public API?
            invoiceNotifier.notify(fullAccount, new DefaultInvoice(invoiceDao.getById(invoice.getId(), context)), buildTenantContext(context));
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

    private void setChargedThroughDates(final Collection<InvoiceItem> fixedPriceItems,
                                        final Collection<InvoiceItem> recurringItems,
                                        final InternalCallContext context) throws SubscriptionBaseApiException {
        final Map<UUID, DateTime> chargeThroughDates = new HashMap<UUID, DateTime>();
        addInvoiceItemsToChargeThroughDates(chargeThroughDates, fixedPriceItems, context);
        addInvoiceItemsToChargeThroughDates(chargeThroughDates, recurringItems, context);

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

        private final Map<UUID, List<SubscriptionNotification>> notifications;

        public FutureAccountNotifications(final Map<UUID, List<SubscriptionNotification>> notifications) {
            this.notifications = notifications;
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

    private List<LocalDate> getUpcomingInvoiceCandidateDates(final Iterable<UUID> filteredSubscriptionIds, final InternalCallContext internalCallContext) {
        final Iterable<DateTime> nextScheduledInvoiceDates = getNextScheduledInvoiceEffectiveDate(filteredSubscriptionIds, internalCallContext);
        final Iterable<DateTime> nextScheduledSubscriptionsEventDates = subscriptionApi.getFutureNotificationsForAccount(internalCallContext);
        return Lists.<DateTime, LocalDate>transform(UPCOMING_NOTIFICATION_DATE_ORDERING.sortedCopy(Iterables.<DateTime>concat(nextScheduledInvoiceDates, nextScheduledSubscriptionsEventDates)),
                                                    new Function<DateTime, LocalDate>() {
                                                        @Override
                                                        public LocalDate apply(final DateTime input) {
                                                            return internalCallContext.toLocalDate(input);
                                                        }
                                                    });
    }

    private Iterable<DateTime> getNextScheduledInvoiceEffectiveDate(final Iterable<UUID> filteredSubscriptionIds, final InternalCallContext internalCallContext) {
        try {
            final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                                                      DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
            final List<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

            final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> allUpcomingEvents = Iterables.filter(futureNotifications, new Predicate<NotificationEventWithMetadata<NextBillingDateNotificationKey>>() {
                @Override
                public boolean apply(@Nullable final NotificationEventWithMetadata<NextBillingDateNotificationKey> input) {
                    final boolean isEventForSubscription = !filteredSubscriptionIds.iterator().hasNext() || Iterables.contains(filteredSubscriptionIds, input.getEvent().getUuidKey());

                    final boolean isEventDryRunForNotifications = input.getEvent().isDryRunForInvoiceNotification() != null ?
                                                                  input.getEvent().isDryRunForInvoiceNotification() : false;
                    return isEventForSubscription && !isEventDryRunForNotifications;
                }
            });

            return Iterables.transform(allUpcomingEvents, new Function<NotificationEventWithMetadata<NextBillingDateNotificationKey>, DateTime>() {
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

    private static final class TargetDateDryRunArguments implements DryRunArguments {

        @Override
        public DryRunType getDryRunType() {
            return DryRunType.TARGET_DATE;
        }

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
        public List<PlanPhasePriceOverride> getPlanPhasePriceOverrides() {
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

    public void processParentInvoiceForInvoiceGeneration(final ImmutableAccountData account, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {

        final InvoiceModelDao childInvoiceModelDao = invoiceDao.getById(childInvoiceId, context);
        final Invoice childInvoice = new DefaultInvoice(childInvoiceModelDao);

        final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(account.getParentAccountId(), ObjectType.ACCOUNT, buildTenantContext(context));
        final InternalCallContext parentContext = internalCallContextFactory.createInternalCallContext(parentAccountRecordId, context);

        BigDecimal childInvoiceAmount = InvoiceCalculatorUtils.computeChildInvoiceAmount(childInvoice.getCurrency(), childInvoice.getInvoiceItems());
        InvoiceModelDao draftParentInvoice = invoiceDao.getParentDraftInvoice(account.getParentAccountId(), parentContext);

        final DateTime today = clock.getNow(account.getTimeZone());
        final String description = account.getExternalKey().concat(" summary");
        if (draftParentInvoice != null) {

            for (InvoiceItemModelDao item : draftParentInvoice.getInvoiceItems()) {
                if ((item.getChildAccountId() != null) && item.getChildAccountId().equals(childInvoice.getAccountId())) {
                    // update child item amount for existing parent invoice item
                    BigDecimal newChildInvoiceAmount = childInvoiceAmount.add(item.getAmount());
                    invoiceDao.updateInvoiceItemAmount(item.getId(), newChildInvoiceAmount, parentContext);
                    return;
                }
            }

            // new item when the parent invoices does not have this child item yet
            final ParentInvoiceItem newParentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), today, draftParentInvoice.getId(), account.getParentAccountId(), account.getId(), childInvoiceAmount, account.getCurrency(), description);
            draftParentInvoice.addInvoiceItem(new InvoiceItemModelDao(newParentInvoiceItem));

            List<InvoiceModelDao> invoices = new ArrayList<InvoiceModelDao>();
            invoices.add(draftParentInvoice);
            invoiceDao.createInvoices(invoices, parentContext);
        } else {
            if (shouldIgnoreChildInvoice(childInvoice, childInvoiceAmount)) {
                return;
            }

            draftParentInvoice = new InvoiceModelDao(account.getParentAccountId(), today.toLocalDate(), account.getCurrency(), InvoiceStatus.DRAFT, true);
            InvoiceItem parentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), today, draftParentInvoice.getId(), account.getParentAccountId(), account.getId(), childInvoiceAmount, account.getCurrency(), description);
            draftParentInvoice.addInvoiceItem(new InvoiceItemModelDao(parentInvoiceItem));

            // build account date time zone
            final FutureAccountNotifications futureAccountNotifications = new FutureAccountNotifications(ImmutableMap.<UUID, List<SubscriptionNotification>>of());

            invoiceDao.createInvoice(draftParentInvoice, draftParentInvoice.getInvoiceItems(), true, futureAccountNotifications, parentContext);
        }

        // save parent child invoice relation
        final InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(draftParentInvoice.getId(), childInvoiceId, account.getId());
        invoiceDao.createParentChildInvoiceRelation(invoiceRelation, parentContext);

    }

    private boolean shouldIgnoreChildInvoice(final Invoice childInvoice, final BigDecimal childInvoiceAmount) {

        switch (childInvoiceAmount.compareTo(BigDecimal.ZERO)) {
            case -1 :
                // do nothing if child invoice has negative amount because it's a credit and it will be use in next invoice
                return true;
            case 1 : return false;
            case 0 :
                // only ignore if amount == 0 and any item is not FIXED or RECURRING
                for (InvoiceItem item : childInvoice.getInvoiceItems()) {
                    if (item.getInvoiceItemType().equals(InvoiceItemType.FIXED) || item.getInvoiceItemType().equals(InvoiceItemType.RECURRING)) return false;
                }
        }

        return true;
    }

    public void processParentInvoiceForAdjustments(final ImmutableAccountData account, final UUID childInvoiceId, final InternalCallContext context) throws InvoiceApiException {

        final InvoiceModelDao childInvoiceModelDao = invoiceDao.getById(childInvoiceId, context);
        final InvoiceModelDao parentInvoiceModelDao = childInvoiceModelDao.getParentInvoice();

        if (parentInvoiceModelDao == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_MISSING_PARENT_INVOICE, childInvoiceModelDao.getId());
        } else if (InvoiceModelDaoHelper.getBalance(parentInvoiceModelDao).compareTo(BigDecimal.ZERO) == 0) {
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
                                                            parentSummaryInvoiceItem.getId());
            parentInvoiceModelDao.addInvoiceItem(new InvoiceItemModelDao(adj));
            invoiceDao.createInvoices(ImmutableList.<InvoiceModelDao>of(parentInvoiceModelDao), parentContext);
            return;
        }

        // update item amount
        final BigDecimal newParentInvoiceItemAmount = childInvoiceAdjustmentAmount.add(parentSummaryInvoiceItem.getAmount());
        invoiceDao.updateInvoiceItemAmount(parentSummaryInvoiceItem.getId(), newParentInvoiceItemAmount, parentContext);
    }

}
