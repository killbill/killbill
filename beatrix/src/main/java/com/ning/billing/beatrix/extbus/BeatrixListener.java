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
package com.ning.billing.beatrix.extbus;

import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.beatrix.extbus.dao.ExtBusEventEntry;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.OverdueChangeInternalEvent;
import com.ning.billing.util.events.SubscriptionInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.common.eventbus.Subscribe;

public class BeatrixListener {

    private static final Logger log = LoggerFactory.getLogger(BeatrixListener.class);

    private final ExternalBus externalBus;
    private final InternalCallContextFactory internalCallContextFactory;
    private final String hostname;

    @Inject
    public BeatrixListener(final ExternalBus externalBus,
            final InternalCallContextFactory internalCallContextFactory) {
        this.externalBus = externalBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.hostname = Hostname.get();
    }

    @Subscribe
    public void handleAllInternalKillbillEvents(final BusInternalEvent event) {
        switch(event.getBusEventType()) {
            case ACCOUNT_CREATE:
                break;
            case ACCOUNT_CHANGE:
                break;
            case SUBSCRIPTION_TRANSITION:
                break;
            case INVOICE_CREATION:
                break;
            case PAYMENT_INFO:
                break;
            case PAYMENT_ERROR:
                break;
            case OVERDUE_CHANGE:
                break;
            default:
                // Ignore for now.
        }
        final ExtBusEventEntry externalEvent = computeExtBusEventEntryFromBusInternalEvent(event);
        try {

            if (externalEvent != null) {
                final InternalCallContext internalContext =  internalCallContextFactory.createInternalCallContext(event.getTenantRecordId(), event.getAccountRecordId(), "BeatrixListener", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                ((PersistentExternalBus) externalBus).post(externalEvent, internalContext);
            }
        }  catch (EventBusException e) {
            log.warn("Failed to post external bus event {} {} ", externalEvent.getExtBusType(), externalEvent.getObjectId());
        }
    }

    private ExtBusEventEntry computeExtBusEventEntryFromBusInternalEvent(final BusInternalEvent event) {

        ObjectType objectType  = null;
        UUID objectId = null;
        ExtBusEventType eventBusType = null;

        switch(event.getBusEventType()) {
        case ACCOUNT_CREATE:
            AccountCreationInternalEvent realEventACR = (AccountCreationInternalEvent) event;
            objectType = ObjectType.ACCOUNT;
            objectId = realEventACR.getId();
            eventBusType = ExtBusEventType.ACCOUNT_CREATION;
            break;

        case ACCOUNT_CHANGE:
            AccountChangeInternalEvent realEventACH = (AccountChangeInternalEvent) event;
            objectType = ObjectType.ACCOUNT;
            objectId = realEventACH.getAccountId();
            eventBusType = ExtBusEventType.ACCOUNT_CHANGE;
            break;

        case SUBSCRIPTION_TRANSITION:
            SubscriptionInternalEvent realEventST  = (SubscriptionInternalEvent) event;
            objectType = ObjectType.SUBSCRIPTION;
            objectId = realEventST.getSubscriptionId();
            if (realEventST.getTransitionType() == SubscriptionTransitionType.CREATE ||
                    realEventST.getTransitionType() == SubscriptionTransitionType.RE_CREATE) {
                eventBusType = ExtBusEventType.SUBSCRIPTION_CREATION;
            } else if (realEventST.getTransitionType() == SubscriptionTransitionType.CANCEL) {
                eventBusType = ExtBusEventType.SUBSCRIPTION_CANCEL;
            } else if (realEventST.getTransitionType() == SubscriptionTransitionType.CHANGE) {
                eventBusType = ExtBusEventType.SUBSCRIPTION_CHANGE;
            }

            break;
        case INVOICE_CREATION:
            break;
        case PAYMENT_INFO:
            break;
        case PAYMENT_ERROR:
            break;
        case OVERDUE_CHANGE:
            OverdueChangeInternalEvent realEventOC = (OverdueChangeInternalEvent) event;
            // TODO When Killbil supports more than overdue for bundle, this will break...
            objectType = ObjectType.BUNDLE;
            objectId = realEventOC.getOverdueObjectId();
            eventBusType = ExtBusEventType.OVERDUE_CHANGE;
            break;
        default:
        }
        return eventBusType != null ?
                new ExtBusEventEntry(hostname, objectType, objectId, eventBusType, event.getAccountRecordId(), event.getAccountRecordId()) : null;
    }

}
