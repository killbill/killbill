/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.entitlement.api.test;

import com.google.inject.Inject;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.engine.core.EventNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DefaultEntitlementTestApi implements EntitlementTestApi {

    private final static Logger log = LoggerFactory.getLogger(DefaultEntitlementTestApi.class);

    private final EventNotifier apiEventProcessor;
    private final EntitlementConfig config;

    @Inject
    public DefaultEntitlementTestApi(EventNotifier apiEventProcessor, EntitlementConfig config) {
        this.apiEventProcessor = apiEventProcessor;
        this.config = config;
    }

    @Override
    public void doProcessReadyEvents(UUID [] subscriptionsIds, Boolean recursive, Boolean oneEventOnly) {
        if (config.isEventProcessingOff()) {
            log.warn("Running event processing loop");
            apiEventProcessor.processAllReadyEvents(subscriptionsIds, recursive, oneEventOnly);
        }
    }
}
