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

package org.killbill.billing.server.notifications;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.jaxrs.json.NotificationJson;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.CallContextFactory;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.NotificationConfig;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class PushNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

    @VisibleForTesting
    public static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";
    @VisibleForTesting
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    private static final int TIMEOUT_NOTIFICATION = 15; // 15 seconds

    private final TenantUserApi tenantApi;
    private final CallContextFactory contextFactory;
    private final AsyncHttpClient httpClient;
    private final ObjectMapper mapper;
    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;
    private final NotificationConfig notificationConfig;

    @Inject
    public PushNotificationListener(final ObjectMapper mapper, final TenantUserApi tenantApi, final CallContextFactory contextFactory,
                                    final NotificationQueueService notificationQueueService, final InternalCallContextFactory internalCallContextFactory,
                                    final Clock clock, final NotificationConfig notificationConfig) {
        this.httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeout(TIMEOUT_NOTIFICATION * 1000).build());
        this.tenantApi = tenantApi;
        this.contextFactory = contextFactory;
        this.mapper = mapper;
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.notificationConfig = notificationConfig;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void triggerPushNotifications(final ExtBusEvent event) {
        final TenantContext context = contextFactory.createTenantContext(event.getAccountId(), event.getTenantId());
        try {
            final List<String> callbacks = getCallbacksForTenant(context);
            if (callbacks.isEmpty()) {
                // Optimization - see https://github.com/killbill/killbill/issues/297
                return;
            }
            dispatchCallback(event.getTenantId(), event, callbacks);
        } catch (final TenantApiException e) {
            log.warn("Failed to retrieve push notification callback for tenant {}", event.getTenantId());
        } catch (final IOException e) {
            log.warn("Failed to retrieve push notification callback for tenant {}", event.getTenantId());
        }
    }

    public void shutdown() {
        httpClient.close();
    }

    private void dispatchCallback(final UUID tenantId, final ExtBusEvent event, final Iterable<String> callbacks) throws IOException {
        final NotificationJson notification = new NotificationJson(event);
        final String body = mapper.writeValueAsString(notification);
        for (final String cur : callbacks) {
            doPost(tenantId, cur, body, notification, TIMEOUT_NOTIFICATION, 0);
        }
    }

    private boolean doPost(final UUID tenantId, final String url, final String body, final NotificationJson notification,
                           final int timeoutSec, final int attemptRetryNumber) {
        log.info("Sending push notification url='{}', body='{}', attemptRetryNumber='{}'", url, body, attemptRetryNumber);
        final BoundRequestBuilder builder = httpClient.preparePost(url);
        builder.setBody(body == null ? "{}" : body);
        builder.addHeader(HTTP_HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);

        final Response response;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(timeoutSec, TimeUnit.SECONDS);
        } catch (final TimeoutException toe) {
            saveRetryPushNotificationInQueue(tenantId, url, notification, attemptRetryNumber, "Timeout");
            return false;
        } catch (final Exception e) {
            log.warn("Failed to push notification url='{}', tenantId='{}'", url, tenantId, e);
            return false;
        }

        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            return true;
        } else {
            saveRetryPushNotificationInQueue(tenantId, url, notification, attemptRetryNumber, "statusCode=" + response.getStatusCode());
            return false;
        }
    }

    public void resendPushNotification(final PushNotificationKey key) throws JsonProcessingException {

        final NotificationJson notification = new NotificationJson(key.getEventType() != null ? key.getEventType().toString() : null,
                                                                   key.getAccountId(),
                                                                   key.getObjectType() != null ? key.getObjectType().toString() : null,
                                                                   key.getObjectId(),
                                                                   key.getMetaData());
        final String body = mapper.writeValueAsString(notification);
        doPost(key.getTenantId(), key.getUrl(), body, notification, TIMEOUT_NOTIFICATION, key.getAttemptNumber());
    }

    private void saveRetryPushNotificationInQueue(final UUID tenantId, final String url, final NotificationJson notificationJson, final int attemptRetryNumber, final String reason) {
        final PushNotificationKey key = new PushNotificationKey(tenantId,
                                                                notificationJson.getAccountId(),
                                                                notificationJson.getEventType(),
                                                                notificationJson.getObjectType(),
                                                                notificationJson.getObjectId(),
                                                                attemptRetryNumber + 1,
                                                                notificationJson.getMetaData(),
                                                                url);

        final TenantContext tenantContext = contextFactory.createTenantContext(null, tenantId);
        final DateTime nextNotificationTime = getNextNotificationTime(key.getAttemptNumber(), internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext));

        if (nextNotificationTime == null) {
            log.warn("Max attempt number reached for push notification url='{}', tenantId='{}'", key.getUrl(), key.getTenantId());
            return;
        }
        log.warn("Push notification {} is re-scheduled to be sent at {}, url='{}', reason='{}'", key, nextNotificationTime, key.getUrl(), reason);

        final Long accountRecordId = internalCallContextFactory.getRecordIdFromObject(key.getAccountId(), ObjectType.ACCOUNT, tenantContext);
        final Long tenantRecordId = internalCallContextFactory.getRecordIdFromObject(key.getTenantId(), ObjectType.TENANT, tenantContext);
        try {
            final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(DefaultServerService.SERVER_SERVICE, PushNotificationRetryService.QUEUE_NAME);
            notificationQueue.recordFutureNotification(nextNotificationTime, key, null, MoreObjects.firstNonNull(accountRecordId, new Long(0)), tenantRecordId);
        } catch (final NoSuchNotificationQueue noSuchNotificationQueue) {
            log.error("Failed to push notification url='{}', tenantId='{}'", key.getUrl(), key.getTenantId(), noSuchNotificationQueue);
        } catch (final IOException e) {
            log.error("Failed to push notification url='{}', tenantId='{}'", key.getUrl(), key.getTenantId(), e);
        }
    }

    private DateTime getNextNotificationTime(final int attemptNumber, final InternalTenantContext tenantContext) {

        final List<TimeSpan> retries = notificationConfig.getPushNotificationsRetries(tenantContext);
        if (attemptNumber > retries.size()) {
            return null;
        }
        final TimeSpan nextDelay = retries.get(attemptNumber - 1);
        return clock.getUTCNow().plusMillis((int) nextDelay.getMillis());
    }

    private List<String> getCallbacksForTenant(final TenantContext context) throws TenantApiException {
        return tenantApi.getTenantValuesForKey(TenantKey.PUSH_NOTIFICATION_CB.toString(), context);
    }
}
