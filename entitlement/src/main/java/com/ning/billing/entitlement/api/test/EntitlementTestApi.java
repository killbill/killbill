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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.IEntitlementConfig;
import com.ning.billing.entitlement.engine.core.IApiEventProcessor;

public class EntitlementTestApi implements IEntitlementTestApi {

    private final static Logger log = LoggerFactory.getLogger(EntitlementTestApi.class);

    private final IApiEventProcessor apiEventProcessor;
    private final IEntitlementConfig config;

    @Inject
    public EntitlementTestApi(IApiEventProcessor apiEventProcessor, IEntitlementConfig config) {
        this.apiEventProcessor = apiEventProcessor;
        this.config = config;
    }

    @Override
    public void doProcessReadyEvents() {
        if (config.isEventProcessingOff()) {
            log.warn("Running event processing loop");
            apiEventProcessor.processAllReadyEvents();
        }
    }
}
