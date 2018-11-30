/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.resources;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.AccountJson;
import org.killbill.billing.jaxrs.json.PaymentMethodJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class ComboPaymentResource extends JaxRsResourceBase {

    @Inject
    public ComboPaymentResource(final JaxrsUriBuilder uriBuilder,
                                final TagUserApi tagUserApi,
                                final CustomFieldUserApi customFieldUserApi,
                                final AuditUserApi auditUserApi,
                                final AccountUserApi accountUserApi,
                                final PaymentApi paymentApi,
                                final InvoicePaymentApi invoicePaymentApi,
                                final Clock clock,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
    }

    protected Account getOrCreateAccount(final AccountJson accountJson, final CallContext callContext) throws AccountApiException {
        // Attempt to retrieve by accountId if specified
        if (accountJson.getAccountId() != null) {
            return accountUserApi.getAccountById(accountJson.getAccountId(), callContext);
        }

        if (accountJson.getExternalKey() != null) {
            // Attempt to retrieve by account externalKey, ignore if does not exist so we can create it with the key specified.
            try {
                return accountUserApi.getAccountByKey(accountJson.getExternalKey(), callContext);
            } catch (final AccountApiException ignore) {
            }
        }
        // Finally create if does not exist
        return accountUserApi.createAccount(accountJson.toAccount(null), callContext);
    }

    protected UUID getOrCreatePaymentMethod(final Account account, @Nullable final PaymentMethodJson paymentMethodJson, final Iterable<PluginProperty> pluginProperties, final CallContext callContext) throws PaymentApiException {

        // No info about payment method was passed, we default to null payment Method ID (which is allowed to be overridden in payment control plugins)
        if (paymentMethodJson == null || paymentMethodJson.getPluginName() == null) {
            return null;
        }

        // Get all payment methods for account
        final List<PaymentMethod> accountPaymentMethods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);

        // If we were specified a paymentMethod id and we find it, we return it
        if (paymentMethodJson.getPaymentMethodId() != null) {
            final UUID match = paymentMethodJson.getPaymentMethodId();
            if (Iterables.any(accountPaymentMethods, new Predicate<PaymentMethod>() {
                @Override
                public boolean apply(final PaymentMethod input) {
                    return input.getId().equals(match);
                }
            })) {
                return match;
            }
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, match);
        }

        // If we were specified a paymentMethod externalKey and we find it, we return it
        if (paymentMethodJson.getExternalKey() != null) {
            final PaymentMethod match = Iterables.tryFind(accountPaymentMethods, new Predicate<PaymentMethod>() {
                @Override
                public boolean apply(final PaymentMethod input) {
                    return input.getExternalKey().equals(paymentMethodJson.getExternalKey());
                }
            }).orNull();
            if (match != null) {
                return match.getId();
            }
        }

        // Only set as default if this is the first paymentMethod on the account
        final boolean isDefault = accountPaymentMethods.isEmpty();
        final PaymentMethod paymentData = paymentMethodJson.toPaymentMethod(account.getId());
        return paymentApi.addPaymentMethod(account, paymentMethodJson.getExternalKey(), paymentMethodJson.getPluginName(), isDefault,
                                           paymentData.getPluginDetail(), pluginProperties, callContext);
    }

}
