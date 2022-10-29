/*
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

package org.killbill.billing.util.optimizer;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.EventConfig;
import org.killbill.bus.api.BusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusDispatcherOptimizerOn implements BusDispatcherOptimizer{

    private static final Logger logger = LoggerFactory.getLogger(BusDispatcherOptimizerOn.class);

    private final EventConfig eventConfig;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public BusDispatcherOptimizerOn(final EventConfig eventConfig, final InternalCallContextFactory internalCallContextFactory) {
        this.eventConfig = eventConfig;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public boolean shouldDispatch(final BusEvent event) {
        Preconditions.checkState(event instanceof BusInternalEvent, "Unexpected external bus event %s, skip...", event);
        final BusInternalEvent internalEvent = (BusInternalEvent) event;

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), null, "BusOptimizerOn", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
        if (eventConfig.getSkipDispatchBusEventTypeList(context).contains(internalEvent.getBusEventType())) {
            logger.debug("BusDispatcherOptimizerOn: Skip dispatching event {}", internalEvent.getBusEventType());
            return false;
        }
        return true;
    }

}
