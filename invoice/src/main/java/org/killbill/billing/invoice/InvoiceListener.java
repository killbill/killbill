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

import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.ChangedField;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.RequestedSubscriptionInternalEvent;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceListenerService;
import org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizer;
import org.killbill.clock.Clock;
import org.killbill.commons.eventbus.AllowConcurrentEvents;
import org.killbill.commons.eventbus.Subscribe;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.retry.RetryableService;
import org.killbill.queue.retry.RetryableSubscriber;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberAction;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("TypeMayBeWeakened")
public class InvoiceListener extends RetryableService implements InvoiceListenerService {

    public static final String INVOICE_LISTENER_SERVICE_NAME = "invoice-listener-service";

    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);

    private final AccountInternalApi accountApi;
    private final InvoiceDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceConfig invoiceConfig;
    private final InvoiceInternalApi invoiceApi;
    private final ParkedAccountsManager parkedAccountsManager;
    private final RetryableSubscriber retryableSubscriber;
    private final BusDispatcherOptimizer busDispatcherOptimizer;
    private final SubscriberQueueHandler subscriberQueueHandler;

    @Inject
    public InvoiceListener(final AccountInternalApi accountApi,
                           final InternalCallContextFactory internalCallContextFactory,
                           final InvoiceDispatcher dispatcher,
                           final InvoiceInternalApi invoiceApi,
                           final InvoiceConfig invoiceConfig,
                           final ParkedAccountsManager parkedAccountsManager,
                           final NotificationQueueService notificationQueueService,
                           final BusDispatcherOptimizer busDispatcherOptimizer,
                           final Clock clock) {
        super(notificationQueueService);
        this.accountApi = accountApi;
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceConfig = invoiceConfig;
        this.invoiceApi = invoiceApi;
        this.parkedAccountsManager = parkedAccountsManager;
        this.busDispatcherOptimizer = busDispatcherOptimizer;
        this.subscriberQueueHandler = new SubscriberQueueHandler();

        subscriberQueueHandler.subscribe(EffectiveSubscriptionInternalEvent.class,
                                         new SubscriberAction<EffectiveSubscriptionInternalEvent>() {
                                             @Override
                                             public void run(final EffectiveSubscriptionInternalEvent event) {
                                                 try {
                                                     //  Skip future uncancel event
                                                     //  Skip events which are marked as not being the last one
                                                     if (event.getTransitionType() == SubscriptionBaseTransitionType.UNCANCEL ||
                                                         event.getRemainingEventsForUserOperation() > 0) {
                                                         return;
                                                     }
                                                     final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                     dispatcher.processSubscriptionForInvoiceGeneration(event, context);
                                                 } catch (final InvoiceApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final RuntimeException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                     throw e;
                                                 }
                                             }
                                         });
        subscriberQueueHandler.subscribe(BlockingTransitionInternalEvent.class,
                                         new SubscriberAction<BlockingTransitionInternalEvent>() {
                                             @Override
                                             public void run(final BlockingTransitionInternalEvent event) {
                                                 // We are only interested in blockBilling or unblockBilling transitions.
                                                 if (!event.isTransitionedToUnblockedBilling() && !event.isTransitionedToBlockedBilling()) {
                                                     return;
                                                 }

                                                 try {
                                                     final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                     final UUID accountId = accountApi.getByRecordId(event.getSearchKey1(), context);
                                                     dispatcher.processAccountFromNotificationOrBusEvent(accountId, null, null, false, context);
                                                 } catch (final InvoiceApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final AccountApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final RuntimeException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                     throw e;
                                                 }
                                             }
                                         });
        subscriberQueueHandler.subscribe(InvoiceCreationInternalEvent.class,
                                         new SubscriberAction<InvoiceCreationInternalEvent>() {
                                             @Override
                                             public void run(final InvoiceCreationInternalEvent event) {
                                                 try {
                                                     final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "CreateParentInvoice", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                     final Account account = accountApi.getAccountById(event.getAccountId(), context);

                                                     // catch children invoices and populate the parent summary invoice
                                                     if (isChildrenAccountAndPaymentDelegated(account)) {
                                                         dispatcher.processParentInvoiceForInvoiceGeneration(account, event.getInvoiceId(), context);
                                                     }

                                                 } catch (final InvoiceApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("CreateParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final AccountApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("CreateParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final RuntimeException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("CreateParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                     throw e;
                                                 }
                                             }
                                         });
        subscriberQueueHandler.subscribe(DefaultInvoiceAdjustmentEvent.class,
                                         new SubscriberAction<DefaultInvoiceAdjustmentEvent>() {
                                             @Override
                                             public void run(final DefaultInvoiceAdjustmentEvent event) {
                                                 try {
                                                     final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "AdjustParentInvoice", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                     final Account account = accountApi.getAccountById(event.getAccountId(), context);

                                                     // catch children invoices and populate the parent summary invoice
                                                     if (isChildrenAccountAndPaymentDelegated(account)) {
                                                         dispatcher.processParentInvoiceForAdjustments(account, event.getInvoiceId(), context);
                                                     }
                                                 } catch (final InvoiceApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("AdjustParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final AccountApiException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("AdjustParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                 } catch (final RuntimeException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("AdjustParentInvoice", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                     throw e;
                                                 }
                                             }
                                         });
        subscriberQueueHandler.subscribe(RequestedSubscriptionInternalEvent.class,
                                         new SubscriberAction<RequestedSubscriptionInternalEvent>() {
                                             @Override
                                             public void run(final RequestedSubscriptionInternalEvent event) {

                                                 if (event.getRemainingEventsForUserOperation() > 0) {
                                                     return;
                                                 }

                                                 try {
                                                     final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "SubscriptionBaseTransition", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                     dispatcher.processSubscriptionStartRequestedDate(event, context);
                                                 } catch (final RuntimeException e) {
                                                     log.warn("Unable to process event {}", event, e);
                                                     handleFailedInvoiceDispatch("SubscriptionBaseTransition", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                     throw e;
                                                 }
                                             }
                                         });
        subscriberQueueHandler.subscribe(AccountChangeInternalEvent.class,
                                         new SubscriberAction<AccountChangeInternalEvent>() {
                                             @Override
                                             public void run(final AccountChangeInternalEvent event) {
                                                 for (final ChangedField changedField : event.getChangedFields()) {
                                                     if ("billCycleDayLocal".equals(changedField.getFieldName()) &&
                                                         !Objects.equals(changedField.getOldValue(), changedField.getNewValue()) &&
                                                         !"0".equals(changedField.getOldValue())) {
                                                         try {
                                                             final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "AccountBCDChange", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                                                             dispatcher.processAccountBCDChange(event.getAccountId(), context);
                                                         } catch (final RuntimeException e) {
                                                             log.warn("Unable to process event {}", event, e);
                                                             handleFailedInvoiceDispatch("AccountBCDChange", event.getSearchKey1(), event.getSearchKey2(), event.getUserToken(), e);
                                                             throw e;
                                                         }
                                                         return;
                                                     }
                                                 }
                                             }
                                         });

        this.retryableSubscriber = new RetryableSubscriber(clock, this, subscriberQueueHandler);
    }

    @Override
    public String getName() {
        return INVOICE_LISTENER_SERVICE_NAME;
    }


    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.INVOICE_SERVICE.getRegistrationOrdering() + 1;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        super.initialize("invoice-listener", subscriberQueueHandler);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        super.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        super.stop();
    }

    private void handleEvent(final BusInternalEvent event) {
        if (busDispatcherOptimizer.shouldDispatch(event)) {
            retryableSubscriber.handleEvent(event);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleSubscriptionTransition(final EffectiveSubscriptionInternalEvent event) {
    	if(!(event.getTransitionType() == SubscriptionBaseTransitionType.EXPIRED)) 
    			handleEvent(event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleBlockingStateTransition(final BlockingTransitionInternalEvent event) {
        handleEvent(event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleSubscriptionTransition(final RequestedSubscriptionInternalEvent event) {
        handleEvent(event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleAccountChange(final AccountChangeInternalEvent event) {
        handleEvent(event);
    }

    public void handleNextBillingDateEvent(final DateTime eventDateTime, final boolean isRescheduled, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "Next Billing Date", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
        try {
            dispatcher.processSubscriptionForInvoiceGeneration(context.toLocalDate(eventDateTime), isRescheduled, context);
        } catch (final InvoiceApiException e) {
            log.warn("Unable to process next billing date event, eventDateTime='{}'", eventDateTime, e);
        }
    }

    public void handleEventForInvoiceNotification(final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "Next Billing Date", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
        try {
            dispatcher.processSubscriptionForInvoiceNotification(context.toLocalDate(eventDateTime), context);
        } catch (final InvoiceApiException e) {
            log.warn("Unable to process event for invoice notification eventDateTime='{}'", eventDateTime, e);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleChildrenInvoiceCreationEvent(final InvoiceCreationInternalEvent event) {
        handleEvent(event);
    }

    private boolean isChildrenAccountAndPaymentDelegated(final Account account) {
        return account.getParentAccountId() != null && account.isPaymentDelegatedToParent();
    }

    public void handleParentInvoiceCommitmentEvent(final UUID invoiceId, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "Commit Invoice", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
            invoiceApi.commitInvoice(invoiceId, context);
        } catch (final InvoiceApiException e) {
            // In case we commit parent invoice earlier we expect to see an INVOICE_INVALID_STATUS status
            if (ErrorCode.INVOICE_INVALID_STATUS.getCode() != e.getCode()) {
                log.error(e.getMessage());
            }
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleChildrenInvoiceAdjustmentEvent(final DefaultInvoiceAdjustmentEvent event) {
        handleEvent(event);
    }

    /**
     * Called when the retry schedule is exhausted for a notification event.
     * Subclasses of RetryableService can override this hook to take remedial action.
     * Note: this method is intended to be called by RetryableService once killbill-commons adds the hook.
     * searchKey1 = accountRecordId, searchKey2 = tenantRecordId.
     */
    public void onRetriesExhausted(final QueueEvent event, final Long searchKey1, final Long searchKey2) {
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(searchKey2, searchKey1, "InvoiceRetryExhausted", CallOrigin.INTERNAL, UserType.SYSTEM, null);
            final UUID accountId = accountApi.getByRecordId(searchKey1, context);
            log.warn("Failed to generate invoice for accountId='{}', all retries exhausted", accountId);
            parkedAccountsManager.parkAccount(accountId, context);
        } catch (final Exception e) {
            log.warn("Unable to park account after retries exhausted for accountRecordId='{}'", searchKey1, e);
        }
    }

    private void handleFailedInvoiceDispatch(final String eventDescription, final Long accountRecordId,
                                             final Long tenantRecordId, final UUID userToken, final Exception e) {
        if (!invoiceConfig.isParkAccountsOnAllExceptions()) {
            return;
        }
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, eventDescription, CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
            final UUID accountId = accountApi.getByRecordId(accountRecordId, context);
            log.warn("Failed to generate invoice for accountId='{}', event='{}'", accountId, eventDescription, e);
            parkedAccountsManager.parkAccount(accountId, context);
        } catch (final Exception inner) {
            log.warn("Unable to park account after invoice failure for accountRecordId='{}'", accountRecordId, inner);
        }
    }
}
