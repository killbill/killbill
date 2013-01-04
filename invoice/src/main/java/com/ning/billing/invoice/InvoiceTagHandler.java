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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.ControlTagDeletionInternalEvent;
import com.ning.billing.util.tag.ControlTagType;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceTagHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTagHandler.class);

    private final Clock clock;
    private final InvoiceDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceTagHandler(final Clock clock,
                             final InvoiceDispatcher dispatcher,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void process_AUTO_INVOICING_OFF_removal(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_INVOICING_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final UUID accountId = event.getObjectId();
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getTenantRecordId(), event.getAccountRecordId(), "InvoiceTagHandler", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            processUnpaid_AUTO_INVOICING_OFF_invoices(accountId, context);
        }
    }

    private void processUnpaid_AUTO_INVOICING_OFF_invoices(final UUID accountId, final InternalCallContext context) {
        try {
            dispatcher.processAccount(accountId, clock.getUTCNow(), false, context);
        } catch (InvoiceApiException e) {
            log.warn(String.format("Failed to process process removal AUTO_INVOICING_OFF for account %s", accountId), e);
        }
    }
}
