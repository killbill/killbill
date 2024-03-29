/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.bus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.payment.api.InvoicePaymentInternalApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentListenerService;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.TimeSpanConverter;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizer;
import org.killbill.billing.util.queue.QueueRetryException;
import org.killbill.clock.Clock;
import org.killbill.commons.eventbus.AllowConcurrentEvents;
import org.killbill.commons.eventbus.Subscribe;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.queue.retry.RetryableService;
import org.killbill.queue.retry.RetryableSubscriber;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberAction;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentBusEventHandler extends RetryableService implements PaymentListenerService {

    public static final String PAYMENT_BUS_HANDLER_NAME = "payment-bus-handler-service";
    private static final Logger log = LoggerFactory.getLogger(PaymentBusEventHandler.class);

    private final Janitor janitor;
    private final RetryableSubscriber retryableSubscriber;
    private final BusDispatcherOptimizer busDispatcherOptimizer;
    private final SubscriberQueueHandler subscriberQueueHandler;

    @Inject
    public PaymentBusEventHandler(final PaymentConfig paymentConfig,
                                  final AccountInternalApi accountApi,
                                  final InvoicePaymentInternalApi invoicePaymentInternalApi,
                                  final Janitor janitor,
                                  final BusDispatcherOptimizer busDispatcherOptimizer,
                                  final NotificationQueueService notificationQueueService,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final Clock clock) {
        super(notificationQueueService);
        this.janitor = janitor;
        this.busDispatcherOptimizer = busDispatcherOptimizer;
        this.subscriberQueueHandler = new SubscriberQueueHandler();

        subscriberQueueHandler.subscribe(InvoiceCreationInternalEvent.class, new SubscriberAction<InvoiceCreationInternalEvent>() {
            @Override
            public void run(final InvoiceCreationInternalEvent event) {
                log.info("Received invoice creation notification for accountId='{}', invoiceId='{}'", event.getAccountId(), event.getInvoiceId());

                final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());

                final BigDecimal amountToBePaid = null; // We let the plugin compute how much should be paid
                final List<String> paymentControlPluginNames = paymentConfig.getPaymentControlPluginNames(internalContext) != null ? new LinkedList<String>(paymentConfig.getPaymentControlPluginNames(internalContext)) : new LinkedList<String>();

                final Account account;
                try {

                    account = accountApi.getAccountById(event.getAccountId(), internalContext);

                    invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                              account,
                                                                              event.getInvoiceId(),
                                                                              account.getPaymentMethodId(),
                                                                              null,
                                                                              amountToBePaid,
                                                                              account.getCurrency(),
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              Collections.emptyList(),
                                                                              new PaymentOptions() {
                                                                                  @Override
                                                                                  public boolean isExternalPayment() {
                                                                                      return false;
                                                                                  }

                                                                                  @Override
                                                                                  public List<String> getPaymentControlPluginNames() {
                                                                                      return paymentControlPluginNames;
                                                                                  }
                                                                              },
                                                                              internalContext);
                } catch (final AccountApiException e) {
                    log.warn("Failed to process invoice payment", e);
                } catch (final PaymentApiException e) {
                    // Look for a LockFailedException cause to decide if we should reschedule
                    // (It should be the first cause of the PaymentApiException, but we support additional levels mostly for testing)
                    Throwable curCause = e.getCause();
                    while (curCause != null) {
                        if (curCause instanceof LockFailedException) {
                            throw new QueueRetryException(e, TimeSpanConverter.toListPeriod(paymentConfig.getRescheduleIntervalOnLock(internalContext)));
                        }
                        curCause = curCause.getCause();
                    }
                    // Log as warn unless nothing left to be paid
                    if (e.getCode() != ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) {
                        log.warn("Failed to process invoice payment", e);
                    }
                }
            }
        });
        this.retryableSubscriber = new RetryableSubscriber(clock, this, subscriberQueueHandler);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void processPaymentEvent(final PaymentInternalEvent event) {
        if (busDispatcherOptimizer.shouldDispatch(event)) {
            janitor.processPaymentEvent(event);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void processInvoiceEvent(final InvoiceCreationInternalEvent event) {
        if (busDispatcherOptimizer.shouldDispatch(event)) {
            retryableSubscriber.handleEvent(event);
        }
    }

    @Override
    public String getName() {
        return PAYMENT_BUS_HANDLER_NAME;
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.PAYMENT_SERVICE.getRegistrationOrdering() + 1;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        super.initialize("payment-bus-handler", subscriberQueueHandler);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        super.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        super.stop();
    }
}


