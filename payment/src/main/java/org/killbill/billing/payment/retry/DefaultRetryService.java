/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.payment.core.PluginControlledPaymentProcessor;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.notificationq.api.NotificationQueueService;

import com.google.inject.Inject;

public class DefaultRetryService extends BaseRetryService implements RetryService {

    public static final String QUEUE_NAME = "retry";

    private final PluginControlledPaymentProcessor processor;

    @Inject
    public DefaultRetryService(final NotificationQueueService notificationQueueService, final InternalCallContextFactory internalCallContextFactory, final PluginControlledPaymentProcessor processor) {
        super(notificationQueueService, internalCallContextFactory);
        this.processor = processor;
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void retry(final UUID paymentId, final Iterable<PluginProperty> properties, final InternalCallContext context) {

    }

    @Override
    public void retryPaymentTransaction(final String transactionExternalKey, final String pluginName, final InternalCallContext context) {
        processor.retryPaymentTransaction(transactionExternalKey, pluginName, context);
    }

    public static class DefaultRetryServiceScheduler extends RetryServiceScheduler {

        @Inject
        public DefaultRetryServiceScheduler(final NotificationQueueService notificationQueueService, final InternalCallContextFactory internalCallContextFactory) {
            super(notificationQueueService, internalCallContextFactory);
        }

        @Override
        public String getQueueName() {
            return QUEUE_NAME;
        }
    }
}
