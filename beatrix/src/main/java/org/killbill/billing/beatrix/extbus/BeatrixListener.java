/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.extbus;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.entitlement.EntitlementTransitionType;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.CustomFieldCreationEvent;
import org.killbill.billing.events.CustomFieldDeletionEvent;
import org.killbill.billing.events.EntitlementInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.OverdueChangeInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.events.SubscriptionInternalEvent;
import org.killbill.billing.events.UserTagCreationInternalEvent;
import org.killbill.billing.events.UserTagDeletionInternalEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.eventbus.Subscribe;

public class BeatrixListener {

    private static final Logger log = LoggerFactory.getLogger(BeatrixListener.class);

    private final PersistentBus externalBus;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountInternalApi accountApi;
    private final NonEntityDao nonEntityDao;

    protected final ObjectMapper objectMapper;

    @Inject
    public BeatrixListener(@Named(BeatrixModule.EXTERNAL_BUS) final PersistentBus externalBus,
                           final InternalCallContextFactory internalCallContextFactory,
                           final AccountInternalApi accountApi,
                           final NonEntityDao nonEntityDao) {
        this.externalBus = externalBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.accountApi = accountApi;
        this.nonEntityDao = nonEntityDao;
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JodaModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Subscribe
    public void handleAllInternalKillbillEvents(final BusInternalEvent event) {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "BeatrixListener", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
        try {
            final BusEvent externalEvent = computeExtBusEventEntryFromBusInternalEvent(event, internalContext);
            if (externalEvent != null) {
                externalBus.post(externalEvent);
            }
        } catch (EventBusException e) {
            log.warn("Failed to dispatch external bus events", e);
        }
    }

    private BusEvent computeExtBusEventEntryFromBusInternalEvent(final BusInternalEvent event, final InternalCallContext context) {

        ObjectType objectType = null;
        UUID objectId = null;
        ExtBusEventType eventBusType = null;

        switch (event.getBusEventType()) {
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
                SubscriptionInternalEvent realEventST = (SubscriptionInternalEvent) event;
                objectType = ObjectType.SUBSCRIPTION;
                objectId = realEventST.getSubscriptionId();
                if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CREATE ||
                    realEventST.getTransitionType() == SubscriptionBaseTransitionType.RE_CREATE ||
                    realEventST.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER ||
                    realEventST.getTransitionType() == SubscriptionBaseTransitionType.MIGRATE_ENTITLEMENT) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CREATION;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CANCEL) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CANCEL;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.PHASE) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_PHASE;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CHANGE) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CHANGE;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.UNCANCEL) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_UNCANCEL;
                }
                break;

            case ENTITLEMENT_TRANSITION:
                EntitlementInternalEvent realEventET = (EntitlementInternalEvent) event;
                objectType = ObjectType.BUNDLE;
                objectId = realEventET.getBundleId();
                if (realEventET.getTransitionType() == EntitlementTransitionType.BLOCK_BUNDLE) {
                    eventBusType = ExtBusEventType.BUNDLE_PAUSE;
                } else if (realEventET.getTransitionType() == EntitlementTransitionType.UNBLOCK_BUNDLE) {
                    eventBusType = ExtBusEventType.BUNDLE_RESUME;
                }
                break;

            case INVOICE_CREATION:
                InvoiceCreationInternalEvent realEventInv = (InvoiceCreationInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInv.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_CREATION;
                break;

            case INVOICE_ADJUSTMENT:
                InvoiceAdjustmentInternalEvent realEventInvAdj = (InvoiceAdjustmentInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInvAdj.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_ADJUSTMENT;
                break;

            case PAYMENT_INFO:
                PaymentInfoInternalEvent realEventPay = (PaymentInfoInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPay.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_SUCCESS;
                break;

            case PAYMENT_ERROR:
                PaymentErrorInternalEvent realEventPayErr = (PaymentErrorInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPayErr.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_FAILED;
                break;

            case PAYMENT_PLUGIN_ERROR:
                PaymentPluginErrorInternalEvent realEventPayPluginErr = (PaymentPluginErrorInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPayPluginErr.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_FAILED;
                break;

            case OVERDUE_CHANGE:
                OverdueChangeInternalEvent realEventOC = (OverdueChangeInternalEvent) event;
                objectType = ObjectType.ACCOUNT;
                objectId = realEventOC.getOverdueObjectId();
                eventBusType = ExtBusEventType.OVERDUE_CHANGE;
                break;

            case USER_TAG_CREATION:
                UserTagCreationInternalEvent realUserTagEventCr = (UserTagCreationInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realUserTagEventCr.getTagId();
                eventBusType = ExtBusEventType.TAG_CREATION;
                break;

            case CONTROL_TAG_CREATION:
                ControlTagCreationInternalEvent realTagEventCr = (ControlTagCreationInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realTagEventCr.getTagId();
                eventBusType = ExtBusEventType.TAG_CREATION;
                break;

            case USER_TAG_DELETION:
                UserTagDeletionInternalEvent realUserTagEventDel = (UserTagDeletionInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realUserTagEventDel.getObjectId();
                eventBusType = ExtBusEventType.TAG_DELETION;
                break;

            case CONTROL_TAG_DELETION:
                ControlTagDeletionInternalEvent realTagEventDel = (ControlTagDeletionInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realTagEventDel.getTagId();
                eventBusType = ExtBusEventType.TAG_DELETION;
                break;

            case CUSTOM_FIELD_CREATION:
                CustomFieldCreationEvent realCustomEveventCr = (CustomFieldCreationEvent) event;
                objectType = ObjectType.CUSTOM_FIELD;
                objectId = realCustomEveventCr.getCustomFieldId();
                eventBusType = ExtBusEventType.CUSTOM_FIELD_CREATION;
                break;

            case CUSTOM_FIELD_DELETION:
                CustomFieldDeletionEvent realCustomEveventDel = (CustomFieldDeletionEvent) event;
                objectType = ObjectType.CUSTOM_FIELD;
                objectId = realCustomEveventDel.getCustomFieldId();
                eventBusType = ExtBusEventType.CUSTOM_FIELD_DELETION;
                break;

            default:
        }
        final UUID accountId = getAccountIdFromRecordId(event.getBusEventType(), objectId, context.getAccountRecordId(), context);
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        return eventBusType != null ?
               new DefaultBusExternalEvent(objectId, objectType, eventBusType, accountId, tenantId, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
               null;
    }

    private final UUID getAccountIdFromRecordId(final BusInternalEventType eventType, final UUID objectId, final Long recordId, final InternalCallContext context) {

        // accountRecord_id is not set for ACCOUNT_CREATE event as we are in the transaction and value is known yet
        if (eventType == BusInternalEventType.ACCOUNT_CREATE) {
            return objectId;
        }
        try {
            final Account account = accountApi.getAccountByRecordId(recordId, context);
            return account.getId();
        } catch (final AccountApiException e) {
            log.warn("Failed to retrieve acount from recordId {}", recordId);
            return null;
        }
    }

}
