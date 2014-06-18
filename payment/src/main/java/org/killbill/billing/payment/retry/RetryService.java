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

package org.killbill.billing.payment.retry;

import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;

public interface RetryService {

    public void initialize(final String svcName) throws NotificationQueueAlreadyExists;

    public void start();

    public void stop() throws NoSuchNotificationQueue;

    public String getQueueName();

    // STEPH_RETRY API disappear
    public void retry(UUID paymentId, final Iterable<PluginProperty> properties, final InternalCallContext context);

    public void retryPaymentTransaction(final String transactionExternalKey, String pluginName, final InternalCallContext context);
}
