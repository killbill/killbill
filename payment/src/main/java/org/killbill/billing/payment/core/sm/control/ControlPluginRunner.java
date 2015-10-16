/*
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
package org.killbill.billing.payment.core.sm.control;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.HPPType;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControlPluginRunner {

    private static final Logger log = LoggerFactory.getLogger(ControlPluginRunner.class);

    private final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;

    @Inject
    public ControlPluginRunner(final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry) {
        this.paymentControlPluginRegistry = paymentControlPluginRegistry;
    }

    public PriorPaymentControlResult executePluginPriorCalls(final Account account,
                                                             final UUID paymentMethodId,
                                                             final UUID paymentAttemptId,
                                                             final UUID paymentId,
                                                             final String paymentExternalKey,
                                                             final String paymentTransactionExternalKey,
                                                             final PaymentApiType paymentApiType,
                                                             final TransactionType transactionType,
                                                             final HPPType hppType,
                                                             final BigDecimal amount,
                                                             final Currency currency,
                                                             final boolean isApiPayment,
                                                             final List<String> paymentControlPluginNames,
                                                             final Iterable<PluginProperty> pluginProperties,
                                                             final CallContext callContext) throws PaymentControlApiException {
        // Return as soon as the first plugin aborts, or the last result for the last plugin
        PriorPaymentControlResult prevResult = new DefaultPriorPaymentControlResult(false, amount, currency, paymentMethodId, pluginProperties);

        // Those values are adjusted prior each call with the result of what previous call to plugin returned
        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        PaymentControlContext inputPaymentControlContext = new DefaultPaymentControlContext(account,
                                                                                            paymentMethodId,
                                                                                            paymentAttemptId,
                                                                                            paymentId,
                                                                                            paymentExternalKey,
                                                                                            paymentTransactionExternalKey,
                                                                                            paymentApiType,
                                                                                            transactionType,
                                                                                            hppType,
                                                                                            amount,
                                                                                            currency,
                                                                                            isApiPayment,
                                                                                            callContext);

        for (final String pluginName : paymentControlPluginNames) {
            final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            if (plugin == null) {
                // First call to plugin, we log warn, if plugin is not registered
                log.warn("Skipping unknown payment control plugin {} when fetching results", pluginName);
                continue;
            }
            log.debug("Calling priorCall of plugin {}", pluginName);
            prevResult = plugin.priorCall(inputPaymentControlContext, inputPluginProperties);
            log.debug("Successful executed priorCall of plugin {}", pluginName);
            if (prevResult.getAdjustedPluginProperties() != null) {
                inputPluginProperties = prevResult.getAdjustedPluginProperties();
            }
            if (prevResult.isAborted()) {
                break;
            }
            inputPaymentControlContext = new DefaultPaymentControlContext(account,
                                                                          prevResult.getAdjustedPaymentMethodId() != null ? prevResult.getAdjustedPaymentMethodId() : paymentMethodId,
                                                                          paymentAttemptId,
                                                                          paymentId,
                                                                          paymentExternalKey,
                                                                          paymentTransactionExternalKey,
                                                                          paymentApiType,
                                                                          transactionType,
                                                                          hppType,
                                                                          prevResult.getAdjustedAmount() != null ? prevResult.getAdjustedAmount() : amount,
                                                                          prevResult.getAdjustedCurrency() != null ? prevResult.getAdjustedCurrency() : currency,
                                                                          isApiPayment,
                                                                          callContext);
        }
        // Rebuild latest result to include inputPluginProperties
        prevResult = new DefaultPriorPaymentControlResult(prevResult, inputPluginProperties);
        return prevResult;
    }

    public OnSuccessPaymentControlResult executePluginOnSuccessCalls(final Account account,
                                                                     final UUID paymentMethodId,
                                                                     final UUID paymentAttemptId,
                                                                     final UUID paymentId,
                                                                     final String paymentExternalKey,
                                                                     final UUID transactionId,
                                                                     final String paymentTransactionExternalKey,
                                                                     final PaymentApiType paymentApiType,
                                                                     final TransactionType transactionType,
                                                                     final HPPType hppType,
                                                                     final BigDecimal amount,
                                                                     final Currency currency,
                                                                     final BigDecimal processedAmount,
                                                                     final Currency processedCurrency,
                                                                     final boolean isApiPayment,
                                                                     final List<String> paymentControlPluginNames,
                                                                     final Iterable<PluginProperty> pluginProperties,
                                                                     final CallContext callContext) {

        final PaymentControlContext inputPaymentControlContext = new DefaultPaymentControlContext(account,
                                                                                                  paymentMethodId,
                                                                                                  paymentAttemptId,
                                                                                                  paymentId,
                                                                                                  paymentExternalKey,
                                                                                                  transactionId,
                                                                                                  paymentTransactionExternalKey,
                                                                                                  paymentApiType,
                                                                                                  transactionType,
                                                                                                  hppType,
                                                                                                  amount,
                                                                                                  currency,
                                                                                                  processedAmount,
                                                                                                  processedCurrency,
                                                                                                  isApiPayment,
                                                                                                  callContext);

        Iterable<PluginProperty> inputPluginProperties = pluginProperties;
        for (final String pluginName : paymentControlPluginNames) {
            final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            if (plugin != null) {
                try {
                    log.debug("Calling onSuccessCall of plugin {}", pluginName);
                    final OnSuccessPaymentControlResult result = plugin.onSuccessCall(inputPaymentControlContext, inputPluginProperties);
                    log.debug("Successful executed onSuccessCall of plugin {}", pluginName);
                    if (result.getAdjustedPluginProperties() != null) {
                        inputPluginProperties = result.getAdjustedPluginProperties();
                    }
                    // Exceptions from the control plugins are ignored (and logged) because the semantics on what to do are undefined.
                } catch (final PaymentControlApiException e) {
                    log.warn("Plugin " + pluginName + " failed to complete executePluginOnSuccessCalls call for " + inputPaymentControlContext.getPaymentExternalKey(), e);
                } catch (final RuntimeException e) {
                    log.warn("Plugin " + pluginName + " failed to complete executePluginOnSuccessCalls call for " + inputPaymentControlContext.getPaymentExternalKey(), e);
                }
            }
        }
        return new DefaultOnSuccessPaymentControlResult(inputPluginProperties);
    }

    public OnFailurePaymentControlResult executePluginOnFailureCalls(final Account account,
                                                                     final UUID paymentMethodId,
                                                                     final UUID paymentAttemptId,
                                                                     final UUID paymentId,
                                                                     final String paymentExternalKey,
                                                                     final String paymentTransactionExternalKey,
                                                                     final PaymentApiType paymentApiType,
                                                                     final TransactionType transactionType,
                                                                     final HPPType hppType,
                                                                     final BigDecimal amount,
                                                                     final Currency currency,
                                                                     final boolean isApiPayment,
                                                                     final List<String> paymentControlPluginNames,
                                                                     final Iterable<PluginProperty> pluginProperties,
                                                                     final CallContext callContext) {

        final PaymentControlContext inputPaymentControlContext = new DefaultPaymentControlContext(account,
                                                                                                  paymentMethodId,
                                                                                                  paymentAttemptId,
                                                                                                  paymentId,
                                                                                                  paymentExternalKey,
                                                                                                  paymentTransactionExternalKey,
                                                                                                  paymentApiType,
                                                                                                  transactionType,
                                                                                                  hppType,
                                                                                                  amount,
                                                                                                  currency,
                                                                                                  isApiPayment,
                                                                                                  callContext);

        DateTime candidate = null;
        Iterable<PluginProperty> inputPluginProperties = pluginProperties;

        for (final String pluginName : paymentControlPluginNames) {
            final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            if (plugin != null) {
                try {
                    log.debug("Calling onSuccessCall of plugin {}", pluginName);
                    final OnFailurePaymentControlResult result = plugin.onFailureCall(inputPaymentControlContext, inputPluginProperties);
                    log.debug("Successful executed onSuccessCall of plugin {}", pluginName);
                    if (candidate == null) {
                        candidate = result.getNextRetryDate();
                    } else if (result.getNextRetryDate() != null) {
                        candidate = candidate.compareTo(result.getNextRetryDate()) > 0 ? result.getNextRetryDate() : candidate;
                    }

                    if (result.getAdjustedPluginProperties() != null) {
                        inputPluginProperties = result.getAdjustedPluginProperties();
                    }

                } catch (final PaymentControlApiException e) {
                    log.warn("Plugin " + pluginName + " failed to return next retryDate for payment " + inputPaymentControlContext.getPaymentExternalKey(), e);
                    return new DefaultFailureCallResult(candidate, inputPluginProperties);
                }
            }
        }
        return new DefaultFailureCallResult(candidate, inputPluginProperties);
    }

    public static class DefaultPaymentControlContext extends DefaultCallContext implements PaymentControlContext {

        private final Account account;
        private final UUID paymentMethodId;
        private final UUID attemptId;
        private final UUID paymentId;
        private final String paymentExternalKey;
        private final UUID transactionId;
        private final String transactionExternalKey;
        private final PaymentApiType paymentApiType;
        private final HPPType hppType;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final BigDecimal processedAmount;
        private final Currency processedCurrency;
        private final boolean isApiPayment;

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey,
                                            final PaymentApiType paymentApiType, final TransactionType transactionType, final HPPType hppType, final BigDecimal amount, final Currency currency,
                                            final boolean isApiPayment, final CallContext callContext) {
            this(account, paymentMethodId, attemptId, paymentId, paymentExternalKey, null, transactionExternalKey, paymentApiType, transactionType, hppType, amount, currency, null, null, isApiPayment, callContext);
        }

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, @Nullable final UUID transactionId, final String transactionExternalKey,
                                            final PaymentApiType paymentApiType, final TransactionType transactionType, final HPPType hppType,
                                            final BigDecimal amount, final Currency currency, @Nullable final BigDecimal processedAmount, @Nullable final Currency processedCurrency, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentMethodId = paymentMethodId;
            this.attemptId = attemptId;
            this.paymentId = paymentId;
            this.paymentExternalKey = paymentExternalKey;
            this.transactionId = transactionId;
            this.transactionExternalKey = transactionExternalKey;
            this.paymentApiType = paymentApiType;
            this.hppType = hppType;
            this.transactionType = transactionType;
            this.amount = amount;
            this.currency = currency;
            this.processedAmount = processedAmount;
            this.processedCurrency = processedCurrency;
            this.isApiPayment = isApiPayment;
        }

        @Override
        public UUID getAccountId() {
            return account.getId();
        }

        @Override
        public String getPaymentExternalKey() {
            return paymentExternalKey;
        }

        @Override
        public String getTransactionExternalKey() {
            return transactionExternalKey;
        }

        @Override
        public PaymentApiType getPaymentApiType() {
            return paymentApiType;
        }

        @Override
        public TransactionType getTransactionType() {
            return transactionType;
        }

        @Override
        public HPPType getHPPType() {
            return hppType;
        }

        @Override
        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public Currency getCurrency() {
            return currency;
        }

        @Override
        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        @Override
        public UUID getPaymentId() {
            return paymentId;
        }

        @Override
        public UUID getAttemptPaymentId() {
            return attemptId;
        }

        @Override
        public BigDecimal getProcessedAmount() {
            return processedAmount;
        }

        @Override
        public Currency getProcessedCurrency() {
            return processedCurrency;
        }

        @Override
        public boolean isApiPayment() {
            return isApiPayment;
        }

        public UUID getTransactionId() {
            return transactionId;
        }

        @Override
        public String toString() {
            return "DefaultPaymentControlContext{" +
                   "account=" + account +
                   ", paymentMethodId=" + paymentMethodId +
                   ", attemptId=" + attemptId +
                   ", paymentId=" + paymentId +
                   ", paymentExternalKey='" + paymentExternalKey + '\'' +
                   ", transactionId=" + transactionId +
                   ", transactionExternalKey='" + transactionExternalKey + '\'' +
                   ", paymentApiType=" + paymentApiType +
                   ", hppType=" + hppType +
                   ", transactionType=" + transactionType +
                   ", amount=" + amount +
                   ", currency=" + currency +
                   ", processedAmount=" + processedAmount +
                   ", processedCurrency=" + processedCurrency +
                   ", isApiPayment=" + isApiPayment +
                   '}';
        }
    }

}
