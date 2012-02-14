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

package com.ning.billing.payment.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.util.bus.Bus;

public class PaymentService implements KillbillService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String SERVICE_NAME = "payment-service";

    private final RequestProcessor requestProcessor;
    private final Bus eventBus;

    @Inject
    public PaymentService(final RequestProcessor requestProcessor, final Bus eventBus) {
        this.requestProcessor = requestProcessor;
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(requestProcessor);
        }
        catch (Bus.EventBusException e) {
            log.error("Unable to register with the EventBus!", e);
        }
    }

}
