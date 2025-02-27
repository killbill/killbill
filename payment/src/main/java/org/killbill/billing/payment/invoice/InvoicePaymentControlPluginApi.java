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

package org.killbill.billing.payment.invoice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentStatus;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.invoice.dao.PluginAutoPayOffModelDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InvoicePaymentControlPluginApi implements PaymentControlPluginApi {

    public static final String CREATED_BY = "InvoicePaymentControlPluginApi";

    public static final String PLUGIN_NAME = "__INVOICE_PAYMENT_CONTROL_PLUGIN__";

    private static final String PROP_IPCD_INVOICE_ID = "IPCD_INVOICE_ID";

    private static final String PROP_IPCD_RETRIES = "IPCD_RETRIES";
    private static final String PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY = "IPCD_REFUND_IDS_AMOUNTS";
    private static final String PROP_IPCD_REFUND_WITH_ADJUSTMENTS = "IPCD_REFUND_WITH_ADJUSTMENTS";
    private static final String PROP_IPCD_PAYMENT_ID = "IPCD_PAYMENT_ID";

    private final PaymentConfig paymentConfig;
    private final InvoiceInternalApi invoiceApi;
    private final TagUserApi tagApi;
    private final PaymentDao paymentDao;
    private final InvoicePaymentControlDao controlDao;
    private final RetryServiceScheduler retryServiceScheduler;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;
    private final AccountInternalApi accountApi;

    private final Logger log = LoggerFactory.getLogger(InvoicePaymentControlPluginApi.class);

    @Inject
    public InvoicePaymentControlPluginApi(final PaymentConfig paymentConfig,
                                          final InvoiceInternalApi invoiceApi, final TagUserApi tagApi,
                                          final PaymentDao paymentDao, final InvoicePaymentControlDao invoicePaymentControlDao,
                                          @Named(PaymentModule.RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler,
                                          final InternalCallContextFactory internalCallContextFactory, final Clock clock,
                                          final AccountInternalApi accountApi) {
        this.paymentConfig = paymentConfig;
        this.invoiceApi = invoiceApi;
        this.tagApi = tagApi;
        this.paymentDao = paymentDao;
        this.controlDao = invoicePaymentControlDao;
        this.retryServiceScheduler = retryServiceScheduler;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.accountApi = accountApi;
    }

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {

        final TransactionType transactionType = paymentControlContext.getTransactionType();
        Preconditions.checkArgument(paymentControlContext.getPaymentApiType() == PaymentApiType.PAYMENT_TRANSACTION, "paymentControlContext.getPaymentApiType() != PaymentApiType.PAYMENT_TRANSACTION");
        Preconditions.checkArgument(transactionType == TransactionType.PURCHASE ||
                                    transactionType == TransactionType.REFUND ||
                                    transactionType == TransactionType.CHARGEBACK ||
                                    transactionType == TransactionType.CREDIT,
                                    "TransactionType should be PURCHASE, REFUND, CHARGEBACK or CREDIT");

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        switch (transactionType) {
            case PURCHASE:
                return getPluginPurchaseResult(paymentControlContext, pluginProperties, internalContext);
            case REFUND:
                return getPluginRefundResult(paymentControlContext, pluginProperties, internalContext);
            case CHARGEBACK:
                return new DefaultPriorPaymentControlResult(false, paymentControlContext.getAmount());
            case CREDIT:
                return getPluginCreditResult(paymentControlContext, pluginProperties, internalContext);
            default:
                throw new IllegalStateException("Unexpected transactionType " + transactionType);
        }
    }


    @Override
    public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final TransactionType transactionType = paymentControlContext.getTransactionType();
        Preconditions.checkArgument(transactionType == TransactionType.PURCHASE ||
                                    transactionType == TransactionType.REFUND ||
                                    transactionType == TransactionType.CHARGEBACK ||
                                    transactionType == TransactionType.CREDIT,
                                    "TransactionType should be PURCHASE, REFUND, CHARGEBACK or CREDIT");

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        try {
            final InvoicePayment existingInvoicePayment;
            final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(paymentControlContext.getTransactionId(), internalContext);
            final InvoicePaymentStatus status = toInvoicePaymentStatus(paymentTransactionModelDao.getTransactionStatus());
            switch (transactionType) {
                case PURCHASE:
                    final UUID invoiceId = getInvoiceId(pluginProperties);
                    existingInvoicePayment = invoiceApi.getInvoicePaymentForAttempt(paymentControlContext.getPaymentId(), internalContext);
                    if (existingInvoicePayment != null && existingInvoicePayment.getStatus() == InvoicePaymentStatus.SUCCESS) {
                        // Only one successful purchase per payment (the invoice could be linked to multiple successful payments though)
                        log.info("onSuccessCall was already completed for purchase paymentId='{}'", paymentControlContext.getPaymentId());
                    } else {
                        final BigDecimal invoicePaymentAmount;
                        if (paymentControlContext.getCurrency() == paymentControlContext.getProcessedCurrency()) {
                            invoicePaymentAmount = paymentControlContext.getProcessedAmount();
                        } else {
                            log.warn("processedCurrency='{}' of invoice paymentId='{}' doesn't match invoice currency='{}', assuming it is a full payment", paymentControlContext.getProcessedCurrency(), paymentControlContext.getPaymentId(), paymentControlContext.getCurrency());
                            invoicePaymentAmount = paymentControlContext.getAmount();
                        }

                        log.debug("Notifying invoice of paymentId='{}', amount='{}', currency='{}', invoiceId='{}', invoicePaymentStatus='{}'", paymentControlContext.getPaymentId(), invoicePaymentAmount, paymentControlContext.getCurrency(), invoiceId, status);

                        invoiceApi.recordPaymentAttemptCompletion(invoiceId,
                                                                  invoicePaymentAmount,
                                                                  paymentControlContext.getCurrency(),
                                                                  paymentControlContext.getProcessedCurrency(),
                                                                  paymentControlContext.getPaymentId(),
                                                                  paymentControlContext.getAttemptPaymentId(),
                                                                  paymentControlContext.getTransactionExternalKey(),
                                                                  paymentControlContext.getCreatedDate(),
                                                                  status,
                                                                  internalContext);
                    }
                    break;

                case REFUND:
                    final Map<UUID, BigDecimal> idWithAmount = extractIdsWithAmountFromProperties(pluginProperties);
                    final PluginProperty prop = getPluginProperty(pluginProperties, PROP_IPCD_REFUND_WITH_ADJUSTMENTS);
                    final boolean isAdjusted = prop != null && prop.getValue() != null ? Boolean.valueOf(prop.getValue().toString()) : false;
                    invoiceApi.recordRefund(paymentControlContext.getPaymentId(), paymentControlContext.getAttemptPaymentId(), paymentControlContext.getAmount(), isAdjusted, idWithAmount, paymentControlContext.getTransactionExternalKey(), status, internalContext);
                    break;

                case CHARGEBACK:
                    existingInvoicePayment = invoiceApi.getInvoicePaymentForChargeback(paymentControlContext.getPaymentId(), internalContext);
                    if (existingInvoicePayment != null) {
                        // We don't support partial chargebacks (yet?)
                        log.info("onSuccessCall was already completed for chargeback paymentId='{}'", paymentControlContext.getPaymentId());
                    } else {
                        final InvoicePayment linkedInvoicePayment = invoiceApi.getInvoicePaymentForAttempt(paymentControlContext.getPaymentId(), internalContext);

                        final BigDecimal amount;
                        final Currency currency;
                        if (linkedInvoicePayment.getCurrency().equals(paymentControlContext.getProcessedCurrency()) && paymentControlContext.getProcessedAmount() != null) {
                            amount = paymentControlContext.getProcessedAmount();
                            currency = paymentControlContext.getProcessedCurrency();
                        } else if (linkedInvoicePayment.getCurrency().equals(paymentControlContext.getCurrency()) && paymentControlContext.getAmount() != null) {
                            amount = paymentControlContext.getAmount();
                            currency = paymentControlContext.getCurrency();
                        } else {
                            amount = linkedInvoicePayment.getAmount();
                            currency = linkedInvoicePayment.getCurrency();
                        }

                        invoiceApi.recordChargeback(paymentControlContext.getPaymentId(), paymentControlContext.getAttemptPaymentId(), paymentControlContext.getTransactionExternalKey(), amount, currency, internalContext);
                    }
                    break;

                case CREDIT:
                    final Map<UUID, BigDecimal> idWithAmountMap = extractIdsWithAmountFromProperties(pluginProperties);
                    final PluginProperty properties = getPluginProperty(pluginProperties, PROP_IPCD_REFUND_WITH_ADJUSTMENTS);
                    final boolean isInvoiceAdjusted = properties != null && properties.getValue() != null ? Boolean.valueOf(properties.getValue().toString()) : false;

                    final PluginProperty legacyPayment = getPluginProperty(pluginProperties, PROP_IPCD_PAYMENT_ID);
                    final UUID paymentId = legacyPayment != null ? (UUID) legacyPayment.getValue() : paymentControlContext.getPaymentId();

                    invoiceApi.recordRefund(paymentId,
                                            paymentControlContext.getAttemptPaymentId(),
                                            paymentControlContext.getAmount(),
                                            isInvoiceAdjusted,
                                            idWithAmountMap,
                                            paymentControlContext.getTransactionExternalKey(),
                                            status,
                                            internalContext);
                    break;

                default:
                    throw new IllegalStateException("Unexpected transactionType " + transactionType);
            }
        } catch (final InvoiceApiException e) {
            log.warn("onSuccessCall failed for attemptId='{}', transactionType='{}'", paymentControlContext.getAttemptPaymentId(), transactionType, e);
        }

        return new DefaultOnSuccessPaymentControlResult();
    }

    @Override
    public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        final TransactionType transactionType = paymentControlContext.getTransactionType();
        final PluginProperty ipcdRetriesProperty = StreamSupport.stream(pluginProperties.spliterator(), false)
                                                                .filter(p -> PROP_IPCD_RETRIES.equals(p.getKey()))
                                                                .findFirst()
                                                                .orElse(null);
        final boolean ipcdRetries = ipcdRetriesProperty != null ? Boolean.parseBoolean(ipcdRetriesProperty.getValue().toString()) : false;
        DateTime nextRetryDate = null;
        switch (transactionType) {
            case PURCHASE:
                final UUID invoiceId = getInvoiceId(pluginProperties);
                try {
                    log.debug("Notifying invoice of failed payment: id={}, amount={}, currency={}, invoiceId={}", paymentControlContext.getPaymentId(), paymentControlContext.getAmount(), paymentControlContext.getCurrency(), invoiceId);
                    invoiceApi.recordPaymentAttemptCompletion(invoiceId,
                                                              BigDecimal.ZERO,
                                                              paymentControlContext.getCurrency(),
                                                              // processed currency may be null so we use currency; processed currency will be updated if/when payment succeeds
                                                              paymentControlContext.getCurrency(),
                                                              paymentControlContext.getPaymentId(),
                                                              paymentControlContext.getAttemptPaymentId(),
                                                              paymentControlContext.getTransactionExternalKey(),
                                                              paymentControlContext.getCreatedDate(),
                                                              InvoicePaymentStatus.INIT,
                                                              internalContext);
                } catch (final InvoiceApiException e) {
                    log.error("InvoicePaymentControlPluginApi onFailureCall failed ton update invoice for attemptId = " + paymentControlContext.getAttemptPaymentId() + ", transactionType  = " + transactionType, e);
                }

                nextRetryDate = computeNextRetryDate(paymentControlContext.getPaymentExternalKey(), ipcdRetries, paymentControlContext.isApiPayment(), internalContext);
                break;
            case CREDIT:
            case REFUND:
                // We don't retry REFUND
                break;
            case CHARGEBACK:
                try {
                    invoiceApi.recordChargebackReversal(paymentControlContext.getPaymentId(), paymentControlContext.getAttemptPaymentId(), paymentControlContext.getTransactionExternalKey(), internalContext);
                } catch (final InvoiceApiException e) {
                    log.warn("onFailureCall failed for attemptId='{}', transactionType='{}'", paymentControlContext.getAttemptPaymentId(), transactionType, e);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected transactionType " + transactionType);
        }

        return new DefaultFailureCallResult(nextRetryDate);
    }

    public void process_AUTO_PAY_OFF_removal(final UUID accountId, final InternalCallContext internalCallContext) {
        final List<PluginAutoPayOffModelDao> entries = controlDao.getAutoPayOffEntry(accountId);
        for (final PluginAutoPayOffModelDao cur : entries) {
            // TODO In theory we should pass not only PLUGIN_NAME, but also all the plugin list associated which the original call
            retryServiceScheduler.scheduleRetry(ObjectType.ACCOUNT, accountId, cur.getAttemptId(), internalCallContext.getTenantRecordId(), List.of(PLUGIN_NAME), internalCallContext.getCreatedDate());
        }
        controlDao.removeAutoPayOffEntry(accountId);
    }

    private static InvoicePaymentStatus toInvoicePaymentStatus(final TransactionStatus status) {
        switch (status) {
            case SUCCESS:
                return InvoicePaymentStatus.SUCCESS;
            case PENDING:
                return InvoicePaymentStatus.PENDING;
            default:
                return InvoicePaymentStatus.INIT;
        }
    }

    private UUID getInvoiceId(final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final PluginProperty invoiceProp = getPluginProperty(pluginProperties, PROP_IPCD_INVOICE_ID);
        if (invoiceProp == null ||
            !(invoiceProp.getValue() instanceof String)) {
            throw new PaymentControlApiException("Failed to retrieve invoiceId: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, String.format("Need to specify a valid invoiceId in property %s", PROP_IPCD_INVOICE_ID)));
        }
        return UUID.fromString((String) invoiceProp.getValue());
    }

    private PriorPaymentControlResult getPluginPurchaseResult(final PaymentControlContext paymentControlPluginContext, final Iterable<PluginProperty> pluginProperties, final InternalCallContext internalContext) throws PaymentControlApiException {
        try {
            final UUID invoiceId = getInvoiceId(pluginProperties);

            // Optimize case where we have a Draft invoice to avoid pulling the whole thing.
            final InvoiceStatus status = invoiceApi.getInvoiceStatus(invoiceId, internalContext);
            if (!InvoiceStatus.COMMITTED.equals(status)) {
                // abort payment if the invoice status is not COMMITTED
                log.info("Aborting payment: invoiceId='{}' is NOT COMMITTED", invoiceId);
                return new DefaultPriorPaymentControlResult(true);
            }

            final Invoice invoice = getAndSanitizeInvoice(invoiceId, paymentControlPluginContext.getAttemptPaymentId(), internalContext);
            // Get account and check if it is child and payment is delegated to parent => abort
            final AccountData accountData = accountApi.getAccountById(invoice.getAccountId(), internalContext);
            if (((accountData != null) && (accountData.getParentAccountId() != null) && accountData.isPaymentDelegatedToParent()) || // Valid when we initially create the child invoice (even if parent invoice does not exist yet)
                (invoice.getParentAccountId() != null))  { // Valid after we have unparented the child
                log.info("Aborting payment: invoiceId='{}' is delegated to parent", invoice.getId());
                return new DefaultPriorPaymentControlResult(true);
            }

            // Is remaining amount > 0?
            final BigDecimal requestedAmount = validateAndComputePaymentAmount(invoice, paymentControlPluginContext.getAmount(), paymentControlPluginContext.isApiPayment());
            if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                if (paymentConfig.allowEmptyInvoice()) {
                    log.info("Not aborting payment for zero amount invoice: invoiceId='{}' since allowEmptyInvoice is set", invoice.getId());
                    return new DefaultPriorPaymentControlResult(false, requestedAmount);
                }
                else {
                    log.info("Aborting payment: invoiceId='{}' has already been paid", invoice.getId());
                    return new DefaultPriorPaymentControlResult(true);

                }
            }

            // Are we in auto-payoff (do the check as soon as possible -- https://github.com/killbill/killbill/issues/812)?
            if (insert_AUTO_PAY_OFF_ifRequired(paymentControlPluginContext, requestedAmount)) {
                log.info("Aborting payment: invoiceId='{}' is AUTO_PAY_OFF", invoice.getId());
                return new DefaultPriorPaymentControlResult(true);
            }

            // Do we have a paymentMethod?
            if (paymentControlPluginContext.getPaymentMethodId() == null) {
                log.warn("Payment for invoiceId='{}' was not triggered, accountId='{}' doesn't have a default payment method", invoiceId, paymentControlPluginContext.getAccountId());
                // This will simply send a bus event
                invoiceApi.recordPaymentAttemptCompletion(invoiceId,
                                                          paymentControlPluginContext.getAmount(),
                                                          paymentControlPluginContext.getCurrency(),
                                                          paymentControlPluginContext.getProcessedCurrency(),
                                                          paymentControlPluginContext.getPaymentId(),
                                                          paymentControlPluginContext.getAttemptPaymentId(),
                                                          paymentControlPluginContext.getTransactionExternalKey(),
                                                          paymentControlPluginContext.getCreatedDate(),
                                                          InvoicePaymentStatus.INIT,
                                                          internalContext);
                return new DefaultPriorPaymentControlResult(true);
            }

            final List<InvoicePayment> existingInvoicePayments = invoiceApi.getInvoicePaymentsByInvoice(invoiceId, internalContext);
            for (final InvoicePayment existingInvoicePayment : existingInvoicePayments) {
                final List<PaymentTransactionModelDao> existingTransactions = paymentDao.getPaymentTransactionsByExternalKey(existingInvoicePayment.getPaymentCookieId(), internalContext);
                for (final PaymentTransactionModelDao existingTransaction : existingTransactions) {
                    if (existingTransaction.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                        log.warn("Existing paymentTransactionId='{}' for invoiceId='{}' in UNKNOWN state", existingTransaction.getId(), invoiceId);
                        return new DefaultPriorPaymentControlResult(true);
                    }
                }
            }

            //
            // Insert attempt row with a success = false status to implement a two-phase commit strategy and guard against scenario where payment would go through
            // but onSuccessCall callback never gets called (leaving the place for a double payment if user retries the operation)
            //
            invoiceApi.recordPaymentAttemptInit(invoice.getId(),
                                                Objects.requireNonNullElse(paymentControlPluginContext.getAmount(), BigDecimal.ZERO),
                                                paymentControlPluginContext.getCurrency(),
                                                paymentControlPluginContext.getCurrency(),
                                                // Likely to be null, but we don't care as we use the transactionExternalKey
                                                // to match the operation in the checkForIncompleteInvoicePaymentAndRepair logic below
                                                paymentControlPluginContext.getPaymentId(),
                                                paymentControlPluginContext.getAttemptPaymentId(),
                                                paymentControlPluginContext.getTransactionExternalKey(),
                                                paymentControlPluginContext.getCreatedDate(),
                                                internalContext);

            return new DefaultPriorPaymentControlResult(false, requestedAmount);


        } catch (final InvoiceApiException e) {
            throw new PaymentControlApiException(e);
        } catch (final IllegalArgumentException e) {
            throw new PaymentControlApiException(e);
        } catch (AccountApiException e) {
            throw new PaymentControlApiException(e);
        }
    }

    private PriorPaymentControlResult getPluginRefundResult(final PaymentControlContext paymentControlPluginContext, final Iterable<PluginProperty> pluginProperties, final InternalCallContext internalContext) throws PaymentControlApiException {
        final Map<UUID, BigDecimal> idWithAmount = extractIdsWithAmountFromProperties(pluginProperties);
        if ((paymentControlPluginContext.getAmount() == null || paymentControlPluginContext.getAmount().compareTo(BigDecimal.ZERO) == 0) &&
            idWithAmount.size() == 0) {
            throw new PaymentControlApiException("Abort refund call: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION,
                                                                                                String.format("Refund for payment, key = %s, aborted: requested refund amount is = %s",
                                                                                                              paymentControlPluginContext.getPaymentExternalKey(),
                                                                                                              paymentControlPluginContext.getAmount())));
        }

        final PaymentModelDao payment = paymentDao.getPayment(paymentControlPluginContext.getPaymentId(), internalContext);
        if (payment == null) {
            throw new PaymentControlApiException("Unexpected null payment");
        }
        // This will calculate the upper bound on the refund amount based on the invoice items associated with that payment.
        // Note that we are not checking that other (partial) refund occurred, but if the refund ends up being greater than what is allowed
        // the call to the gateway would fail; it would need noce to validate on our side though...
        final BigDecimal amountToBeRefunded = computeRefundAmount(payment.getId(), paymentControlPluginContext.getAmount(), idWithAmount, internalContext);
        final boolean isAborted = amountToBeRefunded.compareTo(BigDecimal.ZERO) == 0;

        if (paymentControlPluginContext.isApiPayment() && isAborted) {
            throw new PaymentControlApiException("Abort refund call: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION,
                                                                                                String.format("Refund for payment %s aborted : invoice item sum amount is %s, requested refund amount is = %s",
                                                                                                              payment.getId(),
                                                                                                              amountToBeRefunded,
                                                                                                              paymentControlPluginContext.getAmount())));
        }

        final PluginProperty prop = getPluginProperty(pluginProperties, PROP_IPCD_REFUND_WITH_ADJUSTMENTS);
        final boolean isAdjusted = prop != null && prop.getValue() != null ? Boolean.valueOf(prop.getValue().toString()) : false;
        if (isAdjusted) {
            try {
                invoiceApi.validateInvoiceItemAdjustments(paymentControlPluginContext.getPaymentId(), idWithAmount, internalContext);
            } catch (InvoiceApiException e) {
                throw new PaymentControlApiException(String.format("Refund for payment %s aborted", payment.getId()),
                                                     new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getMessage()));
            }
        }

        return new DefaultPriorPaymentControlResult(isAborted, amountToBeRefunded);
    }

    private PriorPaymentControlResult getPluginCreditResult(final PaymentControlContext paymentControlPluginContext, final Iterable<PluginProperty> pluginProperties, final InternalCallContext internalContext) throws PaymentControlApiException {
        // TODO implement
        return new DefaultPriorPaymentControlResult(false, paymentControlPluginContext.getAmount());
    }

    private Map<UUID, BigDecimal> extractIdsWithAmountFromProperties(final Iterable<PluginProperty> properties) {
        final PluginProperty prop = getPluginProperty(properties, PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY);
        if (prop == null) {
            return Collections.emptyMap();
        }
        // The deserialization may not recreate the map we expect, i.e Map<UUID, BigDecimal>, so we convert each key/value by hand
        // See https://github.com/killbill/killbill/issues/1453
        final Map<UUID, BigDecimal> res = new HashMap<>();
        final Map m = (Map) prop.getValue();
        for (final Object k : m.keySet()) {
            UUID uuid;
            if (k instanceof String) {
                uuid = UUID.fromString((String) k);
            } else if (k instanceof UUID) {
                uuid = (UUID) k;
            } else {
                throw new IllegalStateException(String.format("Failed to deserialize plugin property map for adjustments: Invalid format for UUID, type=%s", k.getClass().getName()));
            }

            final Object v = m.get(k);
            BigDecimal val;
            if (v instanceof BigDecimal) {
                val = (BigDecimal) v;
            } else if (v instanceof String) {
                val = new BigDecimal((String) v);
            } else if (v instanceof Integer) {
                val = new BigDecimal(((Integer) v).toString());
            } else if (v == null) {
                // Null is allowed to default ot item#amount
                val = null;
            } else {
                throw new IllegalStateException(String.format("Failed to deserialize plugin property map for adjustments: Invalid format for BigDecimal, type=%s", v.getClass().getName()));
            }
            res.put(uuid, val);
        }
        return res;
    }

    private PluginProperty getPluginProperty(final Iterable<PluginProperty> properties, final String propertyName) {
        return Iterables.toStream(properties)
                        .filter(input -> input.getKey().equals(propertyName))
                        .findFirst().orElse(null);
    }

    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                                           final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalTenantContext context)
            throws PaymentControlApiException {

        if (specifiedRefundAmount != null) {
            if (specifiedRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentControlApiException("Failed to compute refund: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, "You need to specify a positive refund amount"));
            }
            return specifiedRefundAmount;
        }

        try {
            final List<InvoiceItem> items = invoiceApi.getInvoiceForPaymentId(paymentId, context).getInvoiceItems();
            BigDecimal amountFromItems = BigDecimal.ZERO;
            for (final Entry<UUID, BigDecimal> entry : invoiceItemIdsWithAmounts.entrySet()) {
                final BigDecimal specifiedItemAmount = entry.getValue();
                final BigDecimal itemAmount = getAmountFromItem(items, entry.getKey());
                if (specifiedItemAmount != null &&
                    (specifiedItemAmount.compareTo(BigDecimal.ZERO) <= 0 || specifiedItemAmount.compareTo(itemAmount) > 0)) {
                    throw new PaymentControlApiException("Failed to compute refund: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, "You need to specify a valid invoice item amount"));
                }
                amountFromItems = amountFromItems.add(Objects.requireNonNullElse(specifiedItemAmount, itemAmount));
            }
            return amountFromItems;
        } catch (final InvoiceApiException e) {
            throw new PaymentControlApiException(e);
        }
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) throws PaymentControlApiException {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }
        throw new PaymentControlApiException(String.format("Unable to find invoice item for id %s", itemId), new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, "Invalid plugin properties"));
    }

    private DateTime computeNextRetryDate(final String paymentExternalKey, final boolean ipcdRetries, final boolean isApiAPayment, final InternalCallContext internalContext) {

        // Don't retry call that come from API.
        if (!ipcdRetries && isApiAPayment) {
            return null;
        }

        final List<PaymentTransactionModelDao> purchasedTransactions = getPurchasedTransactions(paymentExternalKey, internalContext);
        if (purchasedTransactions.size() == 0) {
            return null;
        }
        final PaymentTransactionModelDao lastTransaction = purchasedTransactions.get(purchasedTransactions.size() - 1);
        switch (lastTransaction.getTransactionStatus()) {
            case PAYMENT_FAILURE:
                return getNextRetryDateForPaymentFailure(purchasedTransactions, internalContext);

            case PLUGIN_FAILURE:
                return getNextRetryDateForPluginFailure(purchasedTransactions, internalContext);

            case UNKNOWN:
            default:
                return null;
        }
    }

    private DateTime getNextRetryDateForPaymentFailure(final List<PaymentTransactionModelDao> purchasedTransactions, final InternalCallContext internalContext) {

        DateTime result = null;
        final List<Integer> retryDays = paymentConfig.getPaymentFailureRetryDays(internalContext);
        final int attemptsInState = getNumberAttemptsInState(purchasedTransactions, TransactionStatus.PAYMENT_FAILURE);
        final int retryCount = (attemptsInState - 1) >= 0 ? (attemptsInState - 1) : 0;
        if (retryCount < retryDays.size()) {
            final int retryInDays;
            final DateTime nextRetryDate = internalContext.getCreatedDate();
            try {
                retryInDays = retryDays.get(retryCount);
                result = nextRetryDate.plusDays(retryInDays);
                log.debug("Next retryDate={}, retryInDays={}, retryCount={}, now={}", result, retryInDays, retryCount, internalContext.getCreatedDate());
            } catch (final NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }
        }
        return result;
    }

    private DateTime getNextRetryDateForPluginFailure(final List<PaymentTransactionModelDao> purchasedTransactions, final InternalCallContext internalContext) {

        DateTime result = null;
        final int attemptsInState = getNumberAttemptsInState(purchasedTransactions, TransactionStatus.PLUGIN_FAILURE);
        final int retryAttempt = (attemptsInState - 1) >= 0 ? (attemptsInState - 1) : 0;

        if (retryAttempt < paymentConfig.getPluginFailureRetryMaxAttempts(internalContext)) {
            int nbSec = paymentConfig.getPluginFailureInitialRetryInSec(internalContext);
            int remainingAttempts = retryAttempt;
            while (--remainingAttempts > 0) {
                nbSec = nbSec * paymentConfig.getPluginFailureRetryMultiplier(internalContext);
            }
            result = internalContext.getCreatedDate().plusSeconds(nbSec);
            log.debug("Next retryDate={}, retryAttempt={}, now={}", result, retryAttempt, internalContext.getCreatedDate());
        }
        return result;
    }

    @VisibleForTesting
    int getNumberAttemptsInState(@Nullable final Collection<PaymentTransactionModelDao> allTransactions, final TransactionStatus... statuses) {
        if (allTransactions == null || allTransactions.isEmpty()) {
            return 0;
        }
        final List<TransactionStatus> transactionStatuses = List.of(statuses);
        return (int) allTransactions.stream()
                .filter(input -> transactionStatuses.contains(input.getTransactionStatus()))
                .count();
    }

    private List<PaymentTransactionModelDao> getPurchasedTransactions(final String paymentExternalKey, final InternalCallContext internalContext) {
        final PaymentModelDao payment = paymentDao.getPaymentByExternalKey(paymentExternalKey, internalContext);
        if (payment == null) {
            return Collections.emptyList();
        }
        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalContext);
        if (transactions == null || transactions.isEmpty()) {
            return Collections.emptyList();
        }
        return transactions.stream()
                .filter(input -> input.getTransactionType() == TransactionType.PURCHASE)
                .collect(Collectors.toUnmodifiableList());
    }

    private Invoice getAndSanitizeInvoice(final UUID invoiceId, final UUID paymentAttemptId, final InternalCallContext context) throws InvoiceApiException {
        final Invoice invoice = invoiceApi.getInvoiceById(invoiceId, context);
        if (checkForIncompleteInvoicePaymentAndRepair(invoice, paymentAttemptId, context)) {
            // Fetch new repaired 'invoice'
            return invoiceApi.getInvoiceById(invoiceId, context);
        } else {
            return invoice;
        }
    }

    private boolean checkForIncompleteInvoicePaymentAndRepair(final Invoice invoice, final UUID paymentAttemptId, final InternalCallContext internalContext) throws InvoiceApiException {

        final List<InvoicePayment> invoicePayments = invoice.getPayments();

        // Look for ATTEMPT matching that invoiceId that are not successful and extract matching paymentTransaction
        final InvoicePayment incompleteInvoicePayment = invoicePayments
                .stream()
                .filter(input -> input.getType() == InvoicePaymentType.ATTEMPT && input.getStatus() != InvoicePaymentStatus.SUCCESS)
                .findFirst().orElse(null);

        // If such (incomplete) paymentTransaction exists, verify the state of the payment transaction
        if (incompleteInvoicePayment != null) {
            final String transactionExternalKey = incompleteInvoicePayment.getPaymentCookieId();
            final List<PaymentTransactionModelDao> transactions = paymentDao.getPaymentTransactionsByExternalKey(transactionExternalKey, internalContext);
            final PaymentTransactionModelDao successfulTransaction = transactions.stream()
                    .filter(input -> {
                        //
                        // In reality this is more tricky because the matching transaction could be an UNKNOWN or PENDING (unsupported by the plugin) state
                        // In case of UNKNOWN, we don't know what to do: fixing it could result in not paying, and not fixing it could result in double payment
                        // Current code ignores it, which means we might end up in doing a double payment in that very edgy scenario, and customer would have to request a refund.
                        //
                        return input.getTransactionStatus() == TransactionStatus.SUCCESS;
                    })
                    .findFirst().orElse(null);

            if (successfulTransaction != null) {
                log.info(String.format("Detected an incomplete invoicePayment row for invoiceId='%s' and transactionExternalKey='%s', will correct status", invoice.getId(), successfulTransaction.getTransactionExternalKey()));

                invoiceApi.recordPaymentAttemptCompletion(invoice.getId(),
                                                          successfulTransaction.getAmount(),
                                                          successfulTransaction.getCurrency(),
                                                          successfulTransaction.getProcessedCurrency(),
                                                          successfulTransaction.getPaymentId(),
                                                          paymentAttemptId,
                                                          successfulTransaction.getTransactionExternalKey(),
                                                          successfulTransaction.getCreatedDate(),
                                                          InvoicePaymentStatus.SUCCESS,
                                                          internalContext);
                return true;

            }
        }
        return false;
    }

    private BigDecimal validateAndComputePaymentAmount(final Invoice invoice, @Nullable final BigDecimal inputAmount, final boolean isApiPayment) throws PaymentControlApiException {

        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (isApiPayment &&
            inputAmount != null &&
            invoice.getBalance().compareTo(inputAmount) < 0) {
            log.info("invoiceId='{}' has a balance='{}' < paymentAmount='{}'", invoice.getId(), invoice.getBalance().floatValue(), inputAmount.floatValue());
            throw new PaymentControlApiException("Abort purchase call: ", new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION,
                                                                                                  String.format("Invalid amount '%s' for invoice '%s': invoice balance is = '%s'",
                                                                                                                inputAmount,
                                                                                                                invoice.getId(),
                                                                                                                invoice.getBalance())));
        }

        return (inputAmount == null || invoice.getBalance().compareTo(inputAmount) < 0) ? invoice.getBalance() : inputAmount;
    }

    private boolean insert_AUTO_PAY_OFF_ifRequired(final PaymentControlContext paymentControlContext, final BigDecimal computedAmount) {
        if (paymentControlContext.isApiPayment() || !isAccountAutoPayOff(paymentControlContext.getAccountId(), paymentControlContext)) {
            return false;
        }
        final PluginAutoPayOffModelDao data = new PluginAutoPayOffModelDao(paymentControlContext.getAttemptPaymentId(), paymentControlContext.getPaymentExternalKey(), paymentControlContext.getTransactionExternalKey(),
                                                                           paymentControlContext.getAccountId(), PLUGIN_NAME,
                                                                           paymentControlContext.getPaymentId(),
                                                                           computedAmount, paymentControlContext.getCurrency(), CREATED_BY, paymentControlContext.getCreatedDate());
        controlDao.insertAutoPayOff(data);
        return true;
    }

    private boolean isAccountAutoPayOff(final UUID accountId, final CallContext callContext) {
        final List<Tag> accountTags = tagApi.getTagsForAccount(accountId, false, callContext);
        return ControlTagType.isAutoPayOff(accountTags.stream().map(Tag::getTagDefinitionId).collect(Collectors.toUnmodifiableList()));
    }
}
