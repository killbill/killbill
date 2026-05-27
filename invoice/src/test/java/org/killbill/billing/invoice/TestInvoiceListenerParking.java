/*
 * Copyright 2014-2026 The Billing Project, LLC
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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizer;
import org.killbill.clock.ClockMock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.mockito.ArgumentMatchers;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression tests for issue #2208: invoice dispatch failures must park the account so a human can investigate.
 *
 * These tests intentionally avoid the embedded DB to remain portable across environments — they exercise the
 * {@link InvoiceListener#handleFailedInvoiceDispatch(String, Long, Long, UUID, Exception)} hook in isolation.
 */
public class TestInvoiceListenerParking {

    private AccountInternalApi accountApi;
    private InternalCallContextFactory internalCallContextFactory;
    private InvoiceDispatcher dispatcher;
    private InvoiceInternalApi invoiceApi;
    private NotificationQueueService notificationQueueService;
    private BusDispatcherOptimizer busDispatcherOptimizer;
    private ParkedAccountsManager parkedAccountsManager;
    private InvoiceConfig invoiceConfig;
    private ClockMock clock;
    private InternalCallContext callContext;

    private final Long accountRecordId = 11L;
    private final Long tenantRecordId = 22L;
    private final UUID userToken = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        // Use subclass mock-maker to avoid inline-mock issues on newer JVMs (Mockito cannot bytecode-rewrite
        // some bootstrap classes under Java 21+).
        accountApi = Mockito.mock(AccountInternalApi.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        internalCallContextFactory = Mockito.mock(InternalCallContextFactory.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        dispatcher = Mockito.mock(InvoiceDispatcher.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        invoiceApi = Mockito.mock(InvoiceInternalApi.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        notificationQueueService = Mockito.mock(NotificationQueueService.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        busDispatcherOptimizer = Mockito.mock(BusDispatcherOptimizer.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        parkedAccountsManager = Mockito.mock(ParkedAccountsManager.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        invoiceConfig = Mockito.mock(InvoiceConfig.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        clock = new ClockMock();
        callContext = Mockito.mock(InternalCallContext.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));

        Mockito.when(internalCallContextFactory.createInternalCallContext(
                ArgumentMatchers.eq(tenantRecordId),
                ArgumentMatchers.eq(accountRecordId),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(CallOrigin.INTERNAL),
                ArgumentMatchers.eq(UserType.SYSTEM),
                ArgumentMatchers.any())).thenReturn(callContext);

        Mockito.when(accountApi.getByRecordId(ArgumentMatchers.eq(accountRecordId), ArgumentMatchers.any(InternalCallContext.class))).thenReturn(accountId);
    }

    private InvoiceListener newListener() {
        return new InvoiceListener(accountApi, internalCallContextFactory, dispatcher, invoiceApi,
                                   notificationQueueService, busDispatcherOptimizer,
                                   parkedAccountsManager, invoiceConfig, clock);
    }

    @Test(groups = "fast")
    public void testHandleFailedInvoiceDispatchParksAccountWhenConfigEnabled() throws Exception {
        Mockito.when(invoiceConfig.isParkAccountsOnAllExceptions(ArgumentMatchers.any(org.killbill.billing.callcontext.InternalTenantContext.class))).thenReturn(true);

        final InvoiceListener listener = newListener();
        final InvoiceApiException ex = new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, accountId);
        listener.handleFailedInvoiceDispatch("SubscriptionBaseTransition", accountRecordId, tenantRecordId, userToken, ex);

        Mockito.verify(parkedAccountsManager, Mockito.times(1)).parkAccount(ArgumentMatchers.eq(accountId), ArgumentMatchers.any(InternalCallContext.class));
    }

    @Test(groups = "fast")
    public void testHandleFailedInvoiceDispatchDoesNotParkWhenConfigDisabled() throws Exception {
        Mockito.when(invoiceConfig.isParkAccountsOnAllExceptions(ArgumentMatchers.any(org.killbill.billing.callcontext.InternalTenantContext.class))).thenReturn(false);

        final InvoiceListener listener = newListener();
        final InvoiceApiException ex = new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, accountId);
        listener.handleFailedInvoiceDispatch("SubscriptionBaseTransition", accountRecordId, tenantRecordId, userToken, ex);

        Mockito.verify(parkedAccountsManager, Mockito.never()).parkAccount(ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(InternalCallContext.class));
    }

    @Test(groups = "fast")
    public void testHandleFailedInvoiceDispatchSurvivesAccountLookupFailure() throws Exception {
        Mockito.when(invoiceConfig.isParkAccountsOnAllExceptions(ArgumentMatchers.any(org.killbill.billing.callcontext.InternalTenantContext.class))).thenReturn(true);
        Mockito.when(accountApi.getByRecordId(ArgumentMatchers.eq(accountRecordId), ArgumentMatchers.any(InternalCallContext.class)))
               .thenThrow(new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_RECORD_ID, accountRecordId));

        final InvoiceListener listener = newListener();
        final RuntimeException ex = new RuntimeException("boom");
        // Must not propagate: the bus subscriber callers must never re-throw.
        try {
            listener.handleFailedInvoiceDispatch("AccountBCDChange", accountRecordId, tenantRecordId, userToken, ex);
        } catch (final Throwable t) {
            Assert.fail("handleFailedInvoiceDispatch must swallow exceptions, got: " + t);
        }

        Mockito.verify(parkedAccountsManager, Mockito.never()).parkAccount(ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(InternalCallContext.class));
    }
}
