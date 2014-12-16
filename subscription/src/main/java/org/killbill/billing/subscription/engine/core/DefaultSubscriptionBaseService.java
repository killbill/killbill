/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.subscription.engine.core;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.subscription.alignment.PlanAligner;
import org.killbill.billing.subscription.alignment.TimedPhase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultSubscriptionBaseService implements EventListener, SubscriptionBaseService {

    public static final String NOTIFICATION_QUEUE_NAME = "subscription-events";
    public static final String SUBSCRIPTION_SERVICE_NAME = "subscription-service";

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBaseService.class);

    private final Clock clock;
    private final SubscriptionDao dao;
    private final PlanAligner planAligner;
    private final PersistentBus eventBus;
    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;
    private NotificationQueue subscriptionEventQueue;
    private final SubscriptionBaseApiService apiService;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher controllerDispatcher;

    @Inject
    public DefaultSubscriptionBaseService(final Clock clock, final SubscriptionDao dao, final PlanAligner planAligner,
                                          final PersistentBus eventBus,
                                          final NotificationQueueService notificationQueueService,
                                          final InternalCallContextFactory internalCallContextFactory,
                                          final SubscriptionBaseApiService apiService,
                                          final NonEntityDao nonEntityDao,
                                          final CacheControllerDispatcher controllerDispatcher) {
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.eventBus = eventBus;
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
        this.apiService = apiService;
        this.nonEntityDao = nonEntityDao;
        this.controllerDispatcher = controllerDispatcher;
    }

    @Override
    public String getName() {
        return SUBSCRIPTION_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        try {
            final NotificationQueueHandler queueHandler = new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(final NotificationEvent inputKey, final DateTime eventDateTime, final UUID fromNotificationQueueUserToken, final Long accountRecordId, final Long tenantRecordId) {
                    if (!(inputKey instanceof SubscriptionNotificationKey)) {
                        log.error("SubscriptionBase service received an unexpected event type {}" + inputKey.getClass().getName());
                        return;
                    }

                    final SubscriptionNotificationKey key = (SubscriptionNotificationKey) inputKey;
                    final SubscriptionBaseEvent event = dao.getEventById(key.getEventId(), internalCallContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId));
                    if (event == null) {
                        // This can be expected if the event is soft deleted (is_active = 0)
                        log.info("Failed to extract event for notification key {}", inputKey);
                        return;
                    }

                    final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "SubscriptionEventQueue", CallOrigin.INTERNAL, UserType.SYSTEM, fromNotificationQueueUserToken);
                    processEventReady(event, key.getSeqId(), context);
                }
            };

            subscriptionEventQueue = notificationQueueService.createNotificationQueue(SUBSCRIPTION_SERVICE_NAME,
                                                                                      NOTIFICATION_QUEUE_NAME,
                                                                                      queueHandler);
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        subscriptionEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        if (subscriptionEventQueue != null) {
            subscriptionEventQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(subscriptionEventQueue.getServiceName(), subscriptionEventQueue.getQueueName());
        }
    }

    @Override
    public void processEventReady(final SubscriptionBaseEvent event, final int seqId, final InternalCallContext context) {
        if (!event.isActive()) {
            return;
        }


        try {
            final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(event.getSubscriptionId(), context);
            if (subscription == null) {
                log.warn("Failed to retrieve subscription for id %s", event.getSubscriptionId());
                return;
            }
            if (subscription.getActiveVersion() > event.getActiveVersion()) {
                // Skip repaired events
                return;
            }

            //
            // Do any internal processing on that event before we send the event to the bus
            //
            int theRealSeqId = seqId;
            if (event.getType() == EventType.PHASE) {
                onPhaseEvent(subscription, context);
            } else if (event.getType() == EventType.API_USER && subscription.getCategory() == ProductCategory.BASE) {
                final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT, controllerDispatcher.getCacheController(CacheType.OBJECT_ID));
                theRealSeqId = onBasePlanEvent(subscription, (ApiEvent) event, context.toCallContext(tenantId));
            }

            final SubscriptionBaseTransitionData transition = (subscription.getTransitionFromEvent(event, theRealSeqId));
            final EffectiveSubscriptionInternalEvent busEvent = new DefaultEffectiveSubscriptionEvent(transition, subscription.getAlignStartDate(),
                                                                                                      context.getUserToken(),
                                                                                                      context.getAccountRecordId(), context.getTenantRecordId());
            eventBus.post(busEvent);
        } catch (EventBusException e) {
            log.warn("Failed to post subscription event " + event, e);
        } catch (CatalogApiException e) {
            log.warn("Failed to post subscription event " + event, e);
        }
    }

    private void onPhaseEvent(final DefaultSubscriptionBase subscription, final InternalCallContext context) {
        try {
            final DateTime now = clock.getUTCNow();
            final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now, context);
            final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                              PhaseEventData.createNextPhaseEvent(subscription.getId(), subscription.getActiveVersion(),
                                                                                  nextTimedPhase.getPhase().getName(), now, nextTimedPhase.getStartPhase()) :
                                              null;
            if (nextPhaseEvent != null) {
                dao.createNextPhaseEvent(subscription, nextPhaseEvent, context);
            }
        } catch (SubscriptionBaseError e) {
            log.error(String.format("Failed to insert next phase for subscription %s", subscription.getId()), e);
        }
    }

    private int onBasePlanEvent(final DefaultSubscriptionBase baseSubscription, final ApiEvent event, final CallContext context) throws CatalogApiException {
        final Product baseProduct = (baseSubscription.getState() == EntitlementState.CANCELLED) ? null : baseSubscription.getCurrentPlan().getProduct();
        return apiService.cancelAddOnsIfRequired(baseProduct, baseSubscription.getBundleId(), event.getEffectiveDate(), context);
    }
}
