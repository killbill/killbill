/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.tag.ControlTagType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceTagHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTagHandler.class);

    private final InvoiceDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceTagHandler(final InvoiceDispatcher dispatcher,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void process_AUTO_INVOICING_OFF_removal(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_INVOICING_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final UUID accountId = event.getObjectId();
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "InvoiceTagHandler", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            processUnpaid_AUTO_INVOICING_OFF_invoices(accountId, context);
        }
    }

    private void processUnpaid_AUTO_INVOICING_OFF_invoices(final UUID accountId, final InternalCallContext context) {
        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, null, null, context);
        } catch (final InvoiceApiException e) {
            log.warn("Failed to process tag removal AUTO_INVOICING_OFF for accountId='{}'", accountId, e);
        }
    }
}
