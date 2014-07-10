/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;

public interface PaymentInternalApi {

    public Payment createPayment(final Account account, final UUID invoiceId,
                                       @Nullable final BigDecimal amount, final Iterable<PluginProperty> properties, final InternalCallContext internalContext) throws PaymentApiException;

    public Payment getPayment(UUID paymentId, Iterable<PluginProperty> properties, InternalTenantContext context)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethodById(UUID paymentMethodId, boolean includedInactive, Iterable<PluginProperty> properties, InternalTenantContext context)
            throws PaymentApiException;

    public List<Payment> getAccountPayments(UUID accountId, InternalTenantContext context)
            throws PaymentApiException;

    public List<PaymentMethod> getPaymentMethods(Account account, Iterable<PluginProperty> properties, InternalTenantContext context)
            throws PaymentApiException;
}
