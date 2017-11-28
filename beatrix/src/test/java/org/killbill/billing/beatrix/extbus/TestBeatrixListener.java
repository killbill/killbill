/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.events.BroadcastInternalEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.CustomFieldCreationEvent;
import org.killbill.billing.events.CustomFieldDeletionEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.events.InvoicePaymentInternalEvent;
import org.killbill.billing.events.OverdueChangeInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.events.TenantConfigChangeInternalEvent;
import org.killbill.billing.events.TenantConfigDeletionInternalEvent;
import org.killbill.billing.events.UserTagCreationInternalEvent;
import org.killbill.billing.events.UserTagDeletionInternalEvent;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.notification.plugin.api.BroadcastMetadata;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.notification.plugin.api.InvoicePaymentMetadata;
import org.killbill.billing.notification.plugin.api.PaymentMetadata;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestBeatrixListener {

    private static final Long SEARCH_KEY_2 = 9L;
    private static final Long SEARCH_KEY_1 = 10L;
    private static final UUID USER_TOKEN = UUID.randomUUID();
    private static final Long ACCOUNT_RECORD_ID = 11L;
    private static final Long TENANT_RECORD_ID = 12L;
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String METADATA = "metadata";

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final DateTime PAYMENT_DATE = DateTime.now();
    private static final BigDecimal PAYMENT_AMOUNT = BigDecimal.valueOf(13);
    private static final UUID LINKED_INVOICE_PAYMENT_ID = UUID.randomUUID();
    private static final String PAYMENT_COOKIE_ID = "payment cookie id";

    private static final UUID PAYMENT_TRANSACTION_ID = UUID.randomUUID();
    private static final DateTime PAYMENT_EFFECTIVE_DATE = DateTime.now();
    
    private static final String SERVICE_NAME = "service name";
    private static final String BROADCAST_EVENT_TYPE = "broadcast event type";
    private static final String BROADCAST_EVENT_JSON = "{\"key\":\"value\"}";

    private BeatrixListener beatrixListener;
    private PersistentBus externalBus;
    private InternalCallContextFactory internalCallContextFactory;
    private TenantContext tenantContext;
    private ObjectMapper objectMapper;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        externalBus = mock(PersistentBus.class);
        internalCallContextFactory = mock(InternalCallContextFactory.class);
        beatrixListener = new BeatrixListener(externalBus, internalCallContextFactory);

        objectMapper = mock(ObjectMapper.class);
        beatrixListener.objectMapper = objectMapper;

        InternalCallContext internalContext = new InternalCallContext(
                TENANT_RECORD_ID,
                ACCOUNT_RECORD_ID,
                null,  null,
                USER_TOKEN,
                null, null, null, null, null, null, null
        );
        when(internalCallContextFactory.createInternalCallContext(
                SEARCH_KEY_2,
                SEARCH_KEY_1,
                "BeatrixListener",
                CallOrigin.INTERNAL,
                UserType.SYSTEM,
                USER_TOKEN)).thenReturn(internalContext);

        tenantContext = mock(TenantContext.class);
        when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
        when(internalCallContextFactory.createTenantContext(internalContext)).thenReturn(tenantContext);
    }

    @Test(groups = "fast")
    public void testAccountCreate() throws Exception {
        AccountCreationInternalEvent event = mock(AccountCreationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.ACCOUNT_CREATE);
        when(event.getId()).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus, times(1)).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), ACCOUNT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.ACCOUNT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.ACCOUNT_CREATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testAccountChange() throws Exception {
        AccountChangeInternalEvent event = mock(AccountChangeInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.ACCOUNT_CHANGE);
        when(event.getAccountId()).thenReturn(ACCOUNT_ID);

        when(internalCallContextFactory.getAccountId(
                ACCOUNT_ID,
                ObjectType.ACCOUNT,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), ACCOUNT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.ACCOUNT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.ACCOUNT_CHANGE);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testInvoiceCreation() throws Exception {
        InvoiceCreationInternalEvent event = mock(InvoiceCreationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_CREATION);
        when(event.getInvoiceId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.INVOICE,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.INVOICE);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.INVOICE_CREATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testInvoiceNotification() throws Exception {
        InvoiceNotificationInternalEvent event = mock(InvoiceNotificationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_NOTIFICATION);
        when(event.getAccountId()).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertNull(postedEvent.getObjectId());
        assertEquals(postedEvent.getObjectType(), ObjectType.INVOICE);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.INVOICE_NOTIFICATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testInvoiceAdjustment() throws Exception {
        InvoiceAdjustmentInternalEvent event = mock(InvoiceAdjustmentInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_ADJUSTMENT);
        when(event.getInvoiceId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.INVOICE,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.INVOICE);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.INVOICE_ADJUSTMENT);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testInvoicePaymentInfo() throws Exception {
        InvoicePaymentInfoInternalEvent event = mock(InvoicePaymentInfoInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_PAYMENT_INFO);
        when(event.getInvoiceId()).thenReturn(OBJECT_ID);
        provideCommonInvoicePaymentInfo(event);

        ArgumentCaptor<InvoicePaymentMetadata> metadataCaptor = ArgumentCaptor.forClass(InvoicePaymentMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.INVOICE,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.INVOICE);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.INVOICE_PAYMENT_SUCCESS);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithAccountId(postedEvent);

        InvoicePaymentMetadata invoicePaymentMetadata = metadataCaptor.getValue();
        assertInvoicePaymentMetadataFields(invoicePaymentMetadata);
    }

    @Test(groups = "fast")
    public void testInvoicePaymentError() throws Exception {
        InvoicePaymentErrorInternalEvent event = mock(InvoicePaymentErrorInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_PAYMENT_ERROR);
        when(event.getInvoiceId()).thenReturn(OBJECT_ID);
        provideCommonInvoicePaymentInfo(event);

        ArgumentCaptor<InvoicePaymentMetadata> metadataCaptor = ArgumentCaptor.forClass(InvoicePaymentMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.INVOICE,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.INVOICE);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.INVOICE_PAYMENT_FAILED);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithAccountId(postedEvent);

        InvoicePaymentMetadata invoicePaymentMetadata = metadataCaptor.getValue();
        assertInvoicePaymentMetadataFields(invoicePaymentMetadata);
    }

    @Test(groups = "fast")
    public void testPaymentInfo() throws Exception {
        PaymentInfoInternalEvent event = mock(PaymentInfoInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.PAYMENT_INFO);
        when(event.getPaymentId()).thenReturn(OBJECT_ID);
        provideCommonPaymentInfo(event);

        ArgumentCaptor<PaymentMetadata> metadataCaptor = ArgumentCaptor.forClass(PaymentMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.PAYMENT,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.PAYMENT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.PAYMENT_SUCCESS);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithAccountId(postedEvent);

        PaymentMetadata paymentMetadata = metadataCaptor.getValue();
        assertPaymentMetadataFields(paymentMetadata);
    }

    @Test(groups = "fast")
    public void testPaymentError() throws Exception {
        PaymentErrorInternalEvent event = mock(PaymentErrorInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.PAYMENT_ERROR);
        when(event.getPaymentId()).thenReturn(OBJECT_ID);
        when(event.getAccountId()).thenReturn(ACCOUNT_ID);
        provideCommonPaymentInfo(event);

        ArgumentCaptor<PaymentMetadata> metadataCaptor = ArgumentCaptor.forClass(PaymentMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.PAYMENT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.PAYMENT_FAILED);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithAccountId(postedEvent);

        PaymentMetadata paymentMetadata = metadataCaptor.getValue();
        assertPaymentMetadataFields(paymentMetadata);
    }

    @Test(groups = "fast")
    public void testPaymentPluginError() throws Exception {
        PaymentPluginErrorInternalEvent event = mock(PaymentPluginErrorInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.PAYMENT_PLUGIN_ERROR);
        when(event.getPaymentId()).thenReturn(OBJECT_ID);
        provideCommonPaymentInfo(event);

        ArgumentCaptor<PaymentMetadata> metadataCaptor = ArgumentCaptor.forClass(PaymentMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.PAYMENT,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.PAYMENT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.PAYMENT_FAILED);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithAccountId(postedEvent);

        PaymentMetadata paymentMetadata = metadataCaptor.getValue();
        assertPaymentMetadataFields(paymentMetadata);
    }

    @Test(groups = "fast")
    public void testOverdueChange() throws Exception {
        OverdueChangeInternalEvent event = mock(OverdueChangeInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.OVERDUE_CHANGE);
        when(event.getOverdueObjectId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.ACCOUNT,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.ACCOUNT);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.OVERDUE_CHANGE);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testUserTagCreation() throws Exception {
        UserTagCreationInternalEvent event = mock(UserTagCreationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.USER_TAG_CREATION);
        when(event.getTagId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.TAG,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.TAG);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TAG_CREATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testControlTagCreation() throws Exception {
        ControlTagCreationInternalEvent event = mock(ControlTagCreationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.CONTROL_TAG_CREATION);
        when(event.getTagId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.TAG,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.TAG);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TAG_CREATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testUserTagDeletion() throws Exception {
        UserTagDeletionInternalEvent event = mock(UserTagDeletionInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.USER_TAG_DELETION);
        when(event.getTagId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.TAG,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.TAG);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TAG_DELETION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testControlTagDeletion() throws Exception {
        ControlTagDeletionInternalEvent event = mock(ControlTagDeletionInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.CONTROL_TAG_DELETION);
        when(event.getTagId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.TAG,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.TAG);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TAG_DELETION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testCustomFieldCreation() throws Exception {
        CustomFieldCreationEvent event = mock(CustomFieldCreationEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.CUSTOM_FIELD_CREATION);
        when(event.getCustomFieldId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.CUSTOM_FIELD,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.CUSTOM_FIELD_CREATION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testCustomFieldDeletion() throws Exception {
        CustomFieldDeletionEvent event = mock(CustomFieldDeletionEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.CUSTOM_FIELD_DELETION);
        when(event.getCustomFieldId()).thenReturn(OBJECT_ID);

        when(internalCallContextFactory.getAccountId(
                OBJECT_ID,
                ObjectType.CUSTOM_FIELD,
                tenantContext)
            ).thenReturn(ACCOUNT_ID);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.CUSTOM_FIELD_DELETION);
        assertNull(postedEvent.getMetaData());
        assertCommonFieldsWithAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testTenantConfigChange() throws Exception {
        TenantConfigChangeInternalEvent event = mock(TenantConfigChangeInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.TENANT_CONFIG_CHANGE);
        when(event.getId()).thenReturn(OBJECT_ID);
        when(event.getKey()).thenReturn(METADATA);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertEquals(postedEvent.getObjectId(), OBJECT_ID);
        assertEquals(postedEvent.getObjectType(), ObjectType.TENANT_KVS);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TENANT_CONFIG_CHANGE);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithNoAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testTenantConfigDeletion() throws Exception {
        TenantConfigDeletionInternalEvent event = mock(TenantConfigDeletionInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.TENANT_CONFIG_DELETION);
        when(event.getKey()).thenReturn(METADATA);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertNull(postedEvent.getObjectId());
        assertEquals(postedEvent.getObjectType(), ObjectType.TENANT_KVS);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.TENANT_CONFIG_DELETION);
        assertEquals(postedEvent.getMetaData(), METADATA);
        assertCommonFieldsWithNoAccountId(postedEvent);
    }

    @Test(groups = "fast")
    public void testBroadcastService() throws Exception {
        BroadcastInternalEvent event = mock(BroadcastInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.BROADCAST_SERVICE);
        when(event.getServiceName()).thenReturn(SERVICE_NAME);
        when(event.getType()).thenReturn(BROADCAST_EVENT_TYPE);
        when(event.getJsonEvent()).thenReturn(BROADCAST_EVENT_JSON);

        ArgumentCaptor<BroadcastMetadata> metadataCaptor = ArgumentCaptor.forClass(BroadcastMetadata.class);
        when(objectMapper.writeValueAsString(metadataCaptor.capture())).thenReturn(METADATA);

        ArgumentCaptor<BusEvent> eventCaptor = ArgumentCaptor.forClass(BusEvent.class);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus).post(eventCaptor.capture());

        DefaultBusExternalEvent postedEvent = (DefaultBusExternalEvent)eventCaptor.getValue();
        assertNull(postedEvent.getObjectId());
        assertEquals(postedEvent.getObjectType(), ObjectType.SERVICE_BROADCAST);
        assertEquals(postedEvent.getEventType(), ExtBusEventType.BROADCAST_SERVICE);
        assertCommonFieldsWithNoAccountId(postedEvent);

        BroadcastMetadata broadcastMetadata = metadataCaptor.getValue();
        assertEquals(broadcastMetadata.getService(), SERVICE_NAME);
        assertEquals(broadcastMetadata.getCommandType(), BROADCAST_EVENT_TYPE);
        assertEquals(broadcastMetadata.getEventJson(), BROADCAST_EVENT_JSON);
    }

    @Test(groups = "fast")
    public void testInvalidInternalEvent() throws Exception {
        BusInternalEvent event = mock(BusInternalEvent.class);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.BUNDLE_REPAIR);

        beatrixListener.handleAllInternalKillbillEvents(event);

        verify(externalBus, never()).post(any(BusEvent.class));
    }

    @Test(groups = "fast")
    public void testJsonProcessingException() throws Exception {
        InvoicePaymentInfoInternalEvent event = mock(InvoicePaymentInfoInternalEvent.class);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.INVOICE_PAYMENT_INFO);
        when(objectMapper.writeValueAsString(anyObject())).thenThrow(JsonProcessingException.class);

        // Just make sure exception gets swallowed.
        beatrixListener.handleAllInternalKillbillEvents(event);
    }

    @Test(groups = "fast", expectedExceptions = RuntimeException.class)
    public void testEventBusException() throws Exception {
        AccountCreationInternalEvent event = mock(AccountCreationInternalEvent.class);
        provideCommonBusEventInfo(event);
        when(event.getBusEventType()).thenReturn(BusInternalEventType.ACCOUNT_CREATE);
        when(event.getId()).thenReturn(ACCOUNT_ID);

        doThrow(EventBusException.class).when(externalBus).post(any(BusEvent.class));

        beatrixListener.handleAllInternalKillbillEvents(event);
    }



    private void provideCommonBusEventInfo(BusInternalEvent event) {
        when(event.getSearchKey2()).thenReturn(SEARCH_KEY_2);
        when(event.getSearchKey1()).thenReturn(SEARCH_KEY_1);
        when(event.getUserToken()).thenReturn(USER_TOKEN);
    }

    private void assertCommonFieldsWithAccountId(DefaultBusExternalEvent postedEvent) {
        assertEquals(postedEvent.getAccountId(), ACCOUNT_ID);
        assertCommonFields(postedEvent);
    }

    private void assertCommonFieldsWithNoAccountId(DefaultBusExternalEvent postedEvent) {
        assertNull(postedEvent.getAccountId());
        assertCommonFields(postedEvent);
    }

    private void assertCommonFields(DefaultBusExternalEvent postedEvent) {
        assertEquals(postedEvent.getTenantId(), TENANT_ID);
        assertEquals(postedEvent.getSearchKey1(), ACCOUNT_RECORD_ID);
        assertEquals(postedEvent.getSearchKey2(), TENANT_RECORD_ID);
        assertEquals(postedEvent.getUserToken(), USER_TOKEN);
    }

    private void provideCommonInvoicePaymentInfo(InvoicePaymentInternalEvent event) {
        when(event.getPaymentId()).thenReturn(PAYMENT_ID);
        when(event.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        when(event.getPaymentDate()).thenReturn(PAYMENT_DATE);
        when(event.getAmount()).thenReturn(PAYMENT_AMOUNT);
        when(event.getCurrency()).thenReturn(Currency.USD);
        when(event.getLinkedInvoicePaymentId()).thenReturn(LINKED_INVOICE_PAYMENT_ID);
        when(event.getPaymentCookieId()).thenReturn(PAYMENT_COOKIE_ID);
        when(event.getProcessedCurrency()).thenReturn(Currency.EUR);
    }

    private void assertInvoicePaymentMetadataFields(InvoicePaymentMetadata invoicePaymentMetadata) {
        assertEquals(invoicePaymentMetadata.getPaymentId(), PAYMENT_ID);
        assertEquals(invoicePaymentMetadata.getInvoicePaymentType(), InvoicePaymentType.ATTEMPT);
        assertEquals(invoicePaymentMetadata.getPaymentDate(), PAYMENT_DATE);
        assertEquals(invoicePaymentMetadata.getAmount(), PAYMENT_AMOUNT);
        assertEquals(invoicePaymentMetadata.getCurrency(), Currency.USD);
        assertEquals(invoicePaymentMetadata.getLinkedInvoicePaymentId(), LINKED_INVOICE_PAYMENT_ID);
        assertEquals(invoicePaymentMetadata.getPaymentCookieId(), PAYMENT_COOKIE_ID);
        assertEquals(invoicePaymentMetadata.getProcessedCurrency(), Currency.EUR);
    }

    private void provideCommonPaymentInfo(PaymentInternalEvent event) {
        when(event.getPaymentTransactionId()).thenReturn(PAYMENT_TRANSACTION_ID);
        when(event.getAmount()).thenReturn(PAYMENT_AMOUNT);
        when(event.getCurrency()).thenReturn(Currency.USD);
        when(event.getStatus()).thenReturn(TransactionStatus.SUCCESS);
        when(event.getTransactionType()).thenReturn(TransactionType.PURCHASE);
        when(event.getEffectiveDate()).thenReturn(PAYMENT_EFFECTIVE_DATE);
    }

    private void assertPaymentMetadataFields(PaymentMetadata paymentMetadata) {
        assertEquals(paymentMetadata.getPaymentTransactionId(), PAYMENT_TRANSACTION_ID);
        assertEquals(paymentMetadata.getAmount(), PAYMENT_AMOUNT);
        assertEquals(paymentMetadata.getCurrency(), Currency.USD);
        assertEquals(paymentMetadata.getStatus(), TransactionStatus.SUCCESS);
        assertEquals(paymentMetadata.getTransactionType(), TransactionType.PURCHASE);
        assertEquals(paymentMetadata.getEffectiveDate(), PAYMENT_EFFECTIVE_DATE);
    }
}