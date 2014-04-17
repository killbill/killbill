/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.api.svcs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

public class DefaultDirectPaymentApi implements DirectPaymentApi {

    private final DirectPaymentProcessor directPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;

    @Inject
    public DefaultDirectPaymentApi(final DirectPaymentProcessor directPaymentProcessor, final InternalCallContextFactory internalCallContextFactory, final Clock clock) {
        this.directPaymentProcessor = directPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
    }

    @Override
    public DirectPayment createAuthorization(final Account account, final BigDecimal amount, final String externalKey, final CallContext callContext) throws PaymentApiException {
        return directPaymentProcessor.createAuthorization(account, amount, externalKey, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    @Override
    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final CallContext callContext) throws PaymentApiException {
        return directPaymentProcessor.createCapture(account, directPaymentId, amount, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    @Override
    public DirectPayment createPurchase(final Account account, final BigDecimal amount, final String externalKey, final CallContext callContext) throws PaymentApiException {
        return directPaymentProcessor.createPurchase(account, amount, externalKey, internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
    }

    @Override
    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final CallContext callContext) throws PaymentApiException {
        return null;
    }

    @Override
    public DirectPayment createCredit(final Account account, final UUID directPaymentId, final CallContext callContext) throws PaymentApiException {
        return null;
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getAccountPayments(accountId, withPluginInfo, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getPayment(directPaymentId, withPluginInfo, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }
}
