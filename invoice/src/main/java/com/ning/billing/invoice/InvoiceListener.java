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
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.InvoiceApiException;

public class InvoiceListener {
    private final static Logger log = LoggerFactory.getLogger(InvoiceListener.class);
	private InvoiceDispatcher dispatcher;

    @Inject
    public InvoiceListener(InvoiceDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Subscribe
    public void handleSubscriptionTransition(final SubscriptionTransition transition) {
        try {
        	dispatcher.processSubscription(transition);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime) {
        try {
        	dispatcher.processSubscription(subscriptionId, eventDateTime);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }


}
