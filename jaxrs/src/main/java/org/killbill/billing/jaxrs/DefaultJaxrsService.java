/*
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

package org.killbill.billing.jaxrs;

import javax.inject.Inject;

import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultJaxrsService implements JaxrsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultJaxrsService.class);

    private static final String JAXRS_SERVICE_NAME = "jaxrs-service";

    private final JaxrsExecutors jaxrsExecutors;

    @Inject
    public DefaultJaxrsService(final JaxrsExecutors jaxrsExecutors) {
        this.jaxrsExecutors = jaxrsExecutors;
    }

    @Override
    public String getName() {
        return JAXRS_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        jaxrsExecutors.initialize();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        try {
            jaxrsExecutors.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("JaxrsService got interrupted", e);
        }
    }
}
