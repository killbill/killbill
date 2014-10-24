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

package org.killbill.billing.payment.invoice;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class PaymentTagHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentTagHandler.class);

    private final Clock clock;
    private final AccountInternalApi accountApi;
    private final PaymentProcessor paymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final OSGIServiceRegistration<PaymentRoutingPluginApi> paymentControlPluginRegistry;
    private final PaymentRoutingPluginApi invoicePaymentControlPlugin;

    @Inject
    public PaymentTagHandler(final Clock clock,
                             final AccountInternalApi accountApi,
                             final PaymentProcessor paymentProcessor,
                             final OSGIServiceRegistration<PaymentRoutingPluginApi> paymentControlPluginRegistry,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.accountApi = accountApi;
        this.paymentProcessor = paymentProcessor;
        this.paymentControlPluginRegistry = paymentControlPluginRegistry;
        this.invoicePaymentControlPlugin = paymentControlPluginRegistry.getServiceForName(InvoicePaymentRoutingPluginApi.PLUGIN_NAME);
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void process_AUTO_PAY_OFF_removal(final ControlTagDeletionInternalEvent event) {

        if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_PAY_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final UUID accountId = event.getObjectId();
            processUnpaid_AUTO_PAY_OFF_payments(accountId, event.getSearchKey1(), event.getSearchKey2(), event.getUserToken());
        }
    }

    private void processUnpaid_AUTO_PAY_OFF_payments(final UUID accountId, final Long accountRecordId, final Long tenantRecordId, final UUID userToken) {
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId,
                                                                                                                 "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
            final Account account = accountApi.getAccountById(accountId, internalCallContext);
            ((InvoicePaymentRoutingPluginApi) invoicePaymentControlPlugin).process_AUTO_PAY_OFF_removal(account, internalCallContext);

        } catch (AccountApiException e) {
            log.warn(String.format("Failed to process process  removal AUTO_PAY_OFF for account %s", accountId), e);
        }
    }
}
