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

package org.killbill.billing.payment.invoice;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.tag.ControlTagType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class PaymentTagHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentTagHandler.class);

    private final AccountInternalApi accountApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentControlPluginApi invoicePaymentControlPlugin;

    @Inject
    public PaymentTagHandler(final AccountInternalApi accountApi,
                             final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.accountApi = accountApi;
        this.invoicePaymentControlPlugin = paymentControlPluginRegistry.getServiceForName(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void process_AUTO_PAY_OFF_removal(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_PAY_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final UUID accountId = event.getObjectId();
            processUnpaid_AUTO_PAY_OFF_payments(accountId, event.getSearchKey1(), event.getSearchKey2(), event.getUserToken());
        }
    }

    private void processUnpaid_AUTO_PAY_OFF_payments(final UUID accountId, final Long accountRecordId, final Long tenantRecordId, final UUID userToken) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId,
                                                                                                             "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
        ((InvoicePaymentControlPluginApi) invoicePaymentControlPlugin).process_AUTO_PAY_OFF_removal(accountId, internalCallContext);

    }
}
