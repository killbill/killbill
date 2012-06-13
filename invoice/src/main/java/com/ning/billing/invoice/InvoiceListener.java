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

package com.ning.billing.invoice;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;

public class InvoiceListener {
    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);
    private final InvoiceDispatcher dispatcher;
    private final CallContextFactory factory;

    @Inject
    public InvoiceListener(final CallContextFactory factory, final InvoiceDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.factory = factory;
    }

    @Subscribe
    public void handleRepairEntitlementEvent(final RepairEntitlementEvent repairEvent) {
        try {
            final CallContext context = factory.createCallContext("RepairBundle", CallOrigin.INTERNAL, UserType.SYSTEM, repairEvent.getUserToken());
            dispatcher.processAccount(repairEvent.getAccountId(), repairEvent.getEffectiveDate(), false, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    @Subscribe
    public void handleSubscriptionTransition(final SubscriptionEvent transition) {
        try {
            //  Skip future uncancel event
            //  Skip events which are marked as not being the last one
            if (transition.getTransitionType() == SubscriptionTransitionType.UNCANCEL
                    || transition.getRemainingEventsForUserOperation() > 0) {
                return;
            }

            final CallContext context = factory.createCallContext("Transition", CallOrigin.INTERNAL, UserType.SYSTEM, transition.getUserToken());
            dispatcher.processSubscription(transition, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime) {
        try {
            final CallContext context = factory.createCallContext("Next Billing Date", CallOrigin.INTERNAL, UserType.SYSTEM);
            dispatcher.processSubscription(subscriptionId, eventDateTime, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }
}
