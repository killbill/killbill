/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.beatrix.extbus;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.events.BroadcastInternalEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.CustomFieldCreationEvent;
import org.killbill.billing.events.CustomFieldDeletionEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.events.OverdueChangeInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.events.SubscriptionInternalEvent;
import org.killbill.billing.events.TenantConfigChangeInternalEvent;
import org.killbill.billing.events.TenantConfigDeletionInternalEvent;
import org.killbill.billing.events.UserTagCreationInternalEvent;
import org.killbill.billing.events.UserTagDeletionInternalEvent;
import org.killbill.billing.lifecycle.glue.BusModule;
import org.killbill.billing.notification.plugin.api.BlockingStateMetadata;
import org.killbill.billing.notification.plugin.api.BroadcastMetadata;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.notification.plugin.api.InvoiceNotificationMetadata;
import org.killbill.billing.notification.plugin.api.InvoicePaymentMetadata;
import org.killbill.billing.notification.plugin.api.PaymentMetadata;
import org.killbill.billing.notification.plugin.api.SubscriptionMetadata;
import org.killbill.billing.notification.plugin.api.SubscriptionMetadata.ActionType;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class BeatrixListener {

    private static final Logger log = LoggerFactory.getLogger(BeatrixListener.class);

    private final PersistentBus externalBus;
    private final InternalCallContextFactory internalCallContextFactory;

    protected ObjectMapper objectMapper;

    @Inject
    public BeatrixListener(@Named(BusModule.EXTERNAL_BUS_NAMED) final PersistentBus externalBus,
                           final InternalCallContextFactory internalCallContextFactory) {
        this.externalBus = externalBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JodaModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleAllInternalKillbillEvents(final BusInternalEvent event) {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "BeatrixListener", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
        try {
            final BusEvent externalEvent = computeExtBusEventEntryFromBusInternalEvent(event, internalContext);
            if (externalEvent != null) {
                log.info("Sending extBusEvent='{}' from busEvent='{}'", externalEvent, event);
                externalBus.post(externalEvent);
            }
        } catch (final EventBusException e) {
            //
            // When using PersistentBus this should never be reached, because errors are caught at the 'ext' bus level and retried until either success or event row is moved to FAILED status.
            // However when using InMemoryBus, this can happen as there is no retry logic (at the 'ext' bus level) and so we should re-throw at this level to kick-in the retry logic from the 'main' bus
            // (The use of RuntimeException is somewhat arbitrary)
            //
            log.warn("Failed to post event {}", event, e);
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private BusEvent computeExtBusEventEntryFromBusInternalEvent(final BusInternalEvent event, final InternalCallContext context) throws JsonProcessingException {
        ObjectType objectType = null;
        UUID objectId = null;
        ExtBusEventType eventBusType = null;
        String metaData = null;

        UUID accountId = null;
        switch (event.getBusEventType()) {
            case ACCOUNT_CREATE:
                final AccountCreationInternalEvent realEventACR = (AccountCreationInternalEvent) event;
                objectType = ObjectType.ACCOUNT;
                objectId = realEventACR.getId();
                eventBusType = ExtBusEventType.ACCOUNT_CREATION;
                break;

            case ACCOUNT_CHANGE:
                final AccountChangeInternalEvent realEventACH = (AccountChangeInternalEvent) event;
                objectType = ObjectType.ACCOUNT;
                objectId = realEventACH.getAccountId();
                eventBusType = ExtBusEventType.ACCOUNT_CHANGE;
                break;

            case SUBSCRIPTION_TRANSITION:
                final SubscriptionInternalEvent realEventST = (SubscriptionInternalEvent) event;
                objectType = ObjectType.SUBSCRIPTION;
                objectId = realEventST.getSubscriptionId();
                if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CREATE ||
                    realEventST.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CREATION;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CANCEL) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CANCEL;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.PHASE) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_PHASE;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.CHANGE) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_CHANGE;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.UNCANCEL) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_UNCANCEL;
                } else if (realEventST.getTransitionType() == SubscriptionBaseTransitionType.BCD_CHANGE) {
                    eventBusType = ExtBusEventType.SUBSCRIPTION_BCD_CHANGE;
                }

                SubscriptionMetadata.ActionType actionType = (event instanceof EffectiveSubscriptionInternalEvent) ? ActionType.EFFECTIVE : ActionType.REQUESTED;
                final SubscriptionMetadata subscriptionMetadataObj = new SubscriptionMetadata(actionType, realEventST.getBundleExternalKey());
                metaData = objectMapper.writeValueAsString(subscriptionMetadataObj);
                break;

            case BLOCKING_STATE:
                final BlockingTransitionInternalEvent realEventBS = (BlockingTransitionInternalEvent) event;
                if (realEventBS.getBlockingType() == BlockingStateType.ACCOUNT) {
                    objectType = ObjectType.ACCOUNT;
                } else if (realEventBS.getBlockingType() == BlockingStateType.SUBSCRIPTION_BUNDLE) {
                    objectType = ObjectType.BUNDLE;
                } else if (realEventBS.getBlockingType() == BlockingStateType.SUBSCRIPTION) {
                    objectType = ObjectType.SUBSCRIPTION;
                }
                objectId = realEventBS.getBlockableId();

                if (EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(realEventBS.getService())) {
                    if (DefaultEntitlementApi.ENT_STATE_START.equals(realEventBS.getStateName())) {
                        eventBusType = ExtBusEventType.ENTITLEMENT_CREATION;
                    } else if (DefaultEntitlementApi.ENT_STATE_BLOCKED.equals(realEventBS.getStateName())) {
                        eventBusType = ExtBusEventType.BUNDLE_PAUSE;
                    } else if (DefaultEntitlementApi.ENT_STATE_CLEAR.equals(realEventBS.getStateName())) {
                        eventBusType = ExtBusEventType.BUNDLE_RESUME;
                    } else if (DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(realEventBS.getStateName())) {
                        eventBusType = ExtBusEventType.ENTITLEMENT_CANCEL;
                    }
                } else {
                    eventBusType = ExtBusEventType.BLOCKING_STATE;

                    final BlockingStateMetadata blockingStateMetadata = new BlockingStateMetadata(realEventBS.getBlockableId(), realEventBS.getService(), realEventBS.getStateName(), realEventBS.getBlockingType(), realEventBS.getEffectiveDate(),
                                                                                        realEventBS.isTransitionedToBlockedBilling(), realEventBS.isTransitionedToUnblockedBilling(),
                                                                                        realEventBS.isTransitionedToBlockedEntitlement(), realEventBS.isTransitionedToUnblockedEntitlement());
                    metaData = objectMapper.writeValueAsString(blockingStateMetadata);
                }
                break;

            case INVOICE_CREATION:
                final InvoiceCreationInternalEvent realEventInv = (InvoiceCreationInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInv.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_CREATION;
                break;

            case INVOICE_NOTIFICATION:
                final InvoiceNotificationInternalEvent realEventInvNotification = (InvoiceNotificationInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = null;
                accountId = realEventInvNotification.getAccountId(); // has to be set here because objectId is null with a dryRun Invoice
                eventBusType = ExtBusEventType.INVOICE_NOTIFICATION;

                final InvoiceNotificationMetadata invoiceNotificationMetadata = new InvoiceNotificationMetadata(realEventInvNotification.getTargetDate(),
                                                                                                realEventInvNotification.getAmountOwed(),
                                                                                                realEventInvNotification.getCurrency());
                metaData = objectMapper.writeValueAsString(invoiceNotificationMetadata);
                break;


            case INVOICE_ADJUSTMENT:
                final InvoiceAdjustmentInternalEvent realEventInvAdj = (InvoiceAdjustmentInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInvAdj.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_ADJUSTMENT;
                break;

            case INVOICE_PAYMENT_INFO:
                final InvoicePaymentInfoInternalEvent realEventInvPay = (InvoicePaymentInfoInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInvPay.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_PAYMENT_SUCCESS;
                final InvoicePaymentMetadata invoicePaymentInfoMetaDataObj = new InvoicePaymentMetadata(realEventInvPay.getPaymentId(), realEventInvPay.getType(), realEventInvPay.getPaymentDate(), realEventInvPay.getAmount(), realEventInvPay.getCurrency(), realEventInvPay.getLinkedInvoicePaymentId(), realEventInvPay.getPaymentCookieId(), realEventInvPay.getProcessedCurrency());
                metaData = objectMapper.writeValueAsString(invoicePaymentInfoMetaDataObj);
                break;

            case INVOICE_PAYMENT_ERROR:
                final InvoicePaymentErrorInternalEvent realEventInvPayErr = (InvoicePaymentErrorInternalEvent) event;
                objectType = ObjectType.INVOICE;
                objectId = realEventInvPayErr.getInvoiceId();
                eventBusType = ExtBusEventType.INVOICE_PAYMENT_FAILED;
                final InvoicePaymentMetadata invoicePaymentErrorMetaDataObj = new InvoicePaymentMetadata(realEventInvPayErr.getPaymentId(), realEventInvPayErr.getType(), realEventInvPayErr.getPaymentDate(), realEventInvPayErr.getAmount(), realEventInvPayErr.getCurrency(), realEventInvPayErr.getLinkedInvoicePaymentId(), realEventInvPayErr.getPaymentCookieId(), realEventInvPayErr.getProcessedCurrency());
                metaData = objectMapper.writeValueAsString(invoicePaymentErrorMetaDataObj);
                break;

            case PAYMENT_INFO:
                final PaymentInfoInternalEvent realEventPay = (PaymentInfoInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPay.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_SUCCESS;
                final PaymentMetadata paymentInfoMetaDataObj = new PaymentMetadata(realEventPay.getPaymentTransactionId(), realEventPay.getAmount(), realEventPay.getCurrency(), realEventPay.getStatus(), realEventPay.getTransactionType(), realEventPay.getEffectiveDate());
                metaData = objectMapper.writeValueAsString(paymentInfoMetaDataObj);
                break;

            case PAYMENT_ERROR:
                final PaymentErrorInternalEvent realEventPayErr = (PaymentErrorInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPayErr.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_FAILED;
                accountId = realEventPayErr.getAccountId();
                final PaymentMetadata paymentErrorMetaDataObj = new PaymentMetadata(realEventPayErr.getPaymentTransactionId(), realEventPayErr.getAmount(), realEventPayErr.getCurrency(), realEventPayErr.getStatus(), realEventPayErr.getTransactionType(), realEventPayErr.getEffectiveDate());
                metaData = objectMapper.writeValueAsString(paymentErrorMetaDataObj);
                break;

            case PAYMENT_PLUGIN_ERROR:
                final PaymentPluginErrorInternalEvent realEventPayPluginErr = (PaymentPluginErrorInternalEvent) event;
                objectType = ObjectType.PAYMENT;
                objectId = realEventPayPluginErr.getPaymentId();
                eventBusType = ExtBusEventType.PAYMENT_FAILED;
                final PaymentMetadata pluginErrorMetaDataObj = new PaymentMetadata(realEventPayPluginErr.getPaymentTransactionId(), realEventPayPluginErr.getAmount(), realEventPayPluginErr.getCurrency(), realEventPayPluginErr.getStatus(), realEventPayPluginErr.getTransactionType(), realEventPayPluginErr.getEffectiveDate());
                metaData = objectMapper.writeValueAsString(pluginErrorMetaDataObj);
                break;

            case OVERDUE_CHANGE:
                final OverdueChangeInternalEvent realEventOC = (OverdueChangeInternalEvent) event;
                objectType = ObjectType.ACCOUNT;
                objectId = realEventOC.getOverdueObjectId();
                eventBusType = ExtBusEventType.OVERDUE_CHANGE;
                break;

            case USER_TAG_CREATION:
                final UserTagCreationInternalEvent realUserTagEventCr = (UserTagCreationInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realUserTagEventCr.getTagId();
                eventBusType = ExtBusEventType.TAG_CREATION;
                break;

            case CONTROL_TAG_CREATION:
                final ControlTagCreationInternalEvent realTagEventCr = (ControlTagCreationInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realTagEventCr.getTagId();
                eventBusType = ExtBusEventType.TAG_CREATION;
                break;

            case USER_TAG_DELETION:
                final UserTagDeletionInternalEvent realUserTagEventDel = (UserTagDeletionInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realUserTagEventDel.getTagId();
                eventBusType = ExtBusEventType.TAG_DELETION;
                break;

            case CONTROL_TAG_DELETION:
                final ControlTagDeletionInternalEvent realTagEventDel = (ControlTagDeletionInternalEvent) event;
                objectType = ObjectType.TAG;
                objectId = realTagEventDel.getTagId();
                eventBusType = ExtBusEventType.TAG_DELETION;
                break;

            case CUSTOM_FIELD_CREATION:
                final CustomFieldCreationEvent realCustomFieldEventCr = (CustomFieldCreationEvent) event;
                objectType = ObjectType.CUSTOM_FIELD;
                objectId = realCustomFieldEventCr.getCustomFieldId();
                eventBusType = ExtBusEventType.CUSTOM_FIELD_CREATION;
                break;

            case CUSTOM_FIELD_DELETION:
                final CustomFieldDeletionEvent realCustomFieldEventDel = (CustomFieldDeletionEvent) event;
                objectType = ObjectType.CUSTOM_FIELD;
                objectId = realCustomFieldEventDel.getCustomFieldId();
                eventBusType = ExtBusEventType.CUSTOM_FIELD_DELETION;
                break;

            case TENANT_CONFIG_CHANGE:
                final TenantConfigChangeInternalEvent realTenantConfigEventChg = (TenantConfigChangeInternalEvent) event;
                objectType = ObjectType.TENANT_KVS;
                objectId = realTenantConfigEventChg.getId();
                eventBusType = ExtBusEventType.TENANT_CONFIG_CHANGE;
                metaData = realTenantConfigEventChg.getKey();
                break;

            case TENANT_CONFIG_DELETION:
                final TenantConfigDeletionInternalEvent realTenantConfigEventDel = (TenantConfigDeletionInternalEvent) event;
                objectType = ObjectType.TENANT_KVS;
                objectId = null;
                eventBusType = ExtBusEventType.TENANT_CONFIG_DELETION;
                metaData = realTenantConfigEventDel.getKey();
                break;

            case BROADCAST_SERVICE:
                final BroadcastInternalEvent realBroadcastEvent = (BroadcastInternalEvent) event;
                objectType = ObjectType.SERVICE_BROADCAST;
                objectId = null;
                eventBusType = ExtBusEventType.BROADCAST_SERVICE;
                final BroadcastMetadata broadcastMetadata = new BroadcastMetadata(realBroadcastEvent.getServiceName(), realBroadcastEvent.getType(), realBroadcastEvent.getJsonEvent());
                metaData = objectMapper.writeValueAsString(broadcastMetadata);
                break;

            default:
        }

        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(context);
        // See #275
        accountId = (accountId == null) ?
                    getAccountId(event.getBusEventType(), objectId, objectType, tenantContext) :
                    accountId;

        return eventBusType != null ?
               new DefaultBusExternalEvent(objectId, objectType, eventBusType, accountId, tenantContext.getTenantId(), metaData, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
               null;
    }

    private UUID getAccountId(final BusInternalEventType eventType, @Nullable final UUID objectId, final ObjectType objectType, final TenantContext context) {
        // accountRecord_id is not set for ACCOUNT_CREATE event as we are in the transaction and value is known yet
        if (eventType == BusInternalEventType.ACCOUNT_CREATE) {
            return objectId;
        } else if (eventType == BusInternalEventType.TENANT_CONFIG_CHANGE || eventType == BusInternalEventType.TENANT_CONFIG_DELETION) {
            return null;
        } else if (objectId == null) {
            return null;
        } else {
            return internalCallContextFactory.getAccountId(objectId, objectType, context);
        }
    }
}
