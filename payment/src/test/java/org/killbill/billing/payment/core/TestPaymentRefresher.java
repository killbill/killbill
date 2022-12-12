/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.payment.core;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.DefaultPayment;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPaymentRefresher extends PaymentTestSuiteNoDB {

    private static final String SEARCH_KEY = "any";
    private static final Long OFFSET = 1L;
    private static final Long LIMIT = 10L;
    private static final String PLUGIN_NAME = "any plugin";
    private static final List<PluginProperty> PLUGIN_PROPERTIES = Collections.emptyList();

    private final PaymentPluginServiceRegistration paymentPluginRegistrar = Mockito.mock(PaymentPluginServiceRegistration.class);

    private Pagination<PaymentTransactionInfoPlugin> createInfoPlugins() {
        final PaymentTransactionInfoPlugin infoPlugin1 = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(infoPlugin1.getKbPaymentId()).thenReturn(UUIDs.randomUUID());

        final PaymentTransactionInfoPlugin infoPlugin2 = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(infoPlugin2.getKbPaymentId()).thenReturn(UUIDs.randomUUID());

        final UUID id = UUIDs.randomUUID();

        final PaymentTransactionInfoPlugin infoPlugin3 = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(infoPlugin3.getKbPaymentId()).thenReturn(id);

        final PaymentTransactionInfoPlugin infoPlugin4 = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(infoPlugin4.getKbPaymentId()).thenReturn(id);

        final List<PaymentTransactionInfoPlugin> infoPlugins = List.of(infoPlugin1, infoPlugin2, infoPlugin3, infoPlugin4);

        return new DefaultPagination<>(LIMIT, infoPlugins.iterator());
    }

    private Payment anyPayment() {
        final UUID uuid = UUIDs.randomUUID();
        final DateTime now = DateTime.now();
        final PaymentTransaction paymentTransaction = Mockito.mock(PaymentTransaction.class);
        Mockito.when(paymentTransaction.getTransactionType()).thenReturn(TransactionType.AUTHORIZE);
        final PaymentAttempt paymentAttempt = Mockito.mock(PaymentAttempt.class);

        return new DefaultPayment(uuid, now, now, uuid, uuid, 1, "ext-key", List.of(paymentTransaction), List.of(paymentAttempt));
    }

    private PaymentPluginApi createPaymentPluginApi(final Pagination<PaymentTransactionInfoPlugin> infoPlugins) throws PaymentPluginApiException {
        final PaymentPluginApi paymentPluginApi = Mockito.mock(PaymentPluginApi.class);
        Mockito.when(paymentPluginApi.searchPayments(SEARCH_KEY, OFFSET, LIMIT, PLUGIN_PROPERTIES, callContext))
               .thenReturn(infoPlugins);

        return paymentPluginApi;
    }

    private PaymentRefresher createPaymentRefresher(final Pagination<PaymentTransactionInfoPlugin> infoPlugins) throws PaymentApiException, PaymentPluginApiException {
        final PaymentPluginApi paymentPluginApi = createPaymentPluginApi(infoPlugins);
        Mockito.when(paymentPluginRegistrar.getPaymentPluginApi(PLUGIN_NAME)).thenReturn(paymentPluginApi);

        final PaymentRefresher result = new PaymentRefresher(paymentPluginRegistrar,
                                                             accountInternalApi,
                                                             paymentDao,
                                                             null, // tagInternalApi / tagUserApi
                                                             null, // GlobalLocker / locker
                                                             internalCallContextFactory,
                                                             invoiceApi,
                                                             clock,
                                                             null, // notificationQueueService
                                                             null /* incompletePaymentTransactionTask */);
        final PaymentRefresher toMock = Mockito.spy(result);
        Mockito.doReturn(anyPayment())
               .when(toMock).toPayment(Mockito.any(UUID.class),
                                       Mockito.anyIterable(),
                                       Mockito.anyBoolean(),
                                       Mockito.anyBoolean(),
                                       Mockito.any());
        return toMock;
    }

    @Test(groups = "fast")
    public void testSearchPayments() throws PaymentApiException, PaymentPluginApiException {
        final boolean withPluginInfo = true;
        final boolean withAttempts = true;
        final boolean isApiPayment = true;

        // infoPlugins contains 4 data, 2 of them contains the same getKbPaymentId() value
        final Pagination<PaymentTransactionInfoPlugin> infoPlugins = createInfoPlugins();
        final PaymentRefresher refresher = createPaymentRefresher(infoPlugins);

        final Pagination<Payment> payments = refresher.searchPayments(SEARCH_KEY,
                                                                      OFFSET,
                                                                      LIMIT,
                                                                      PLUGIN_NAME,
                                                                      withPluginInfo,
                                                                      withAttempts,
                                                                      isApiPayment,
                                                                      PLUGIN_PROPERTIES,
                                                                      callContext,
                                                                      internalCallContext);
        Assert.assertEquals(Iterables.size(payments), 3);
        Mockito.verify(refresher, Mockito.times(3))
               .toPayment(Mockito.any(UUID.class),
                              Mockito.anyIterable(),
                              Mockito.anyBoolean(),
                              Mockito.anyBoolean(),
                              Mockito.any(InternalTenantContext.class));
    }
}
