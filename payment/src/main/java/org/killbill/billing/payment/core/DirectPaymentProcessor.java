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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultDirectPayment;
import org.killbill.billing.payment.api.DefaultDirectPaymentTransaction;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class DirectPaymentProcessor extends ProcessorBase {

    private final Clock clock;

    private final PaymentConfig paymentConfig;

    private final PluginDispatcher<Payment> paymentPluginDispatcher;
    private final PluginDispatcher<Void> voidPluginDispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    private static final Logger log = LoggerFactory.getLogger(DirectPaymentProcessor.class);

    @Inject
    public DirectPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                  final AccountInternalApi accountUserApi,
                                  final InvoiceInternalApi invoiceApi,
                                  final TagInternalApi tagUserApi,
                                  final PaymentDao paymentDao,
                                  final NonEntityDao nonEntityDao,
                                  final PersistentBus eventBus,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final Clock clock,
                                  final GlobalLocker locker,
                                  final PaymentConfig paymentConfig,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentConfig = paymentConfig;
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<Payment>(paymentPluginTimeoutSec, executor);
        this.voidPluginDispatcher = new PluginDispatcher<Void>(paymentPluginTimeoutSec, executor);
    }

    public DirectPayment createAuthorization(final Account account, final BigDecimal amount, final String externalKey, final Iterable<PluginProperty> properties, final InternalCallContext callContext) throws PaymentApiException {

        final PaymentPluginApi plugin = getPaymentProviderPlugin(account, callContext);

        final DateTime utcNow = clock.getUTCNow();
        final DirectPaymentModelDao pmd = new DirectPaymentModelDao(utcNow, utcNow, account.getId(), account.getPaymentMethodId(), externalKey);
        final DirectPaymentTransactionModelDao ptmd = new DirectPaymentTransactionModelDao(utcNow, utcNow, pmd.getId(),
                                                                                           TransactionType.AUTHORIZE, utcNow, PaymentStatus.UNKNOWN,
                                                                                           amount, account.getCurrency(), null, null);

        final DirectPaymentModelDao inserted = paymentDao.insertDirectPaymentWithFirstTransaction(pmd, ptmd, callContext);
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(callContext.getTenantRecordId(), ObjectType.TENANT);

        PaymentStatus paymentStatus;
        PaymentInfoPlugin infoPlugin;
        try {

            try {
                infoPlugin = plugin.authorizePayment(account.getId(), pmd.getId(), ptmd.getId(), amount, account.getCurrency(), properties, callContext.toCallContext(tenantId));
            } catch (final RuntimeException e) {
                // Handle case of plugin RuntimeException to be handled the same as a Plugin failure (PaymentPluginApiException)
                final String formatError = String.format("Plugin threw RuntimeException for payment %s", pmd.getId());
                throw new PaymentPluginApiException(formatError, e);
            }

            switch (infoPlugin.getStatus()) {
                case PROCESSED:
                case PENDING:
                    // Update Payment/PaymentAttempt status
                    paymentStatus = infoPlugin.getStatus() == PaymentPluginStatus.PROCESSED ? PaymentStatus.SUCCESS : PaymentStatus.PENDING;
                    paymentDao.updateDirectPaymentAndTransactionOnCompletion(pmd.getId(), paymentStatus, amount, account.getCurrency(),
                                                                             ptmd.getId(), infoPlugin.getGatewayErrorCode(), null, callContext);
                    break;

                case ERROR:
                    paymentStatus = PaymentStatus.PLUGIN_FAILURE_ABORTED;
                    paymentDao.updateDirectPaymentAndTransactionOnCompletion(pmd.getId(), paymentStatus, amount, account.getCurrency(),
                                                                             ptmd.getId(), infoPlugin.getGatewayErrorCode(), infoPlugin.getGatewayError(), callContext);
                    break;

                case UNDEFINED:
                default:
                    final String formatError = String.format("Plugin return status %s for payment %s", infoPlugin.getStatus(), pmd.getId());
                    // This caught right below as a retryable Plugin failure
                    throw new PaymentPluginApiException("", formatError);
            }
        } catch (final PaymentPluginApiException e) {
            paymentStatus = PaymentStatus.PAYMENT_FAILURE_ABORTED;
            infoPlugin = null;
            paymentDao.updateDirectPaymentAndTransactionOnCompletion(pmd.getId(), paymentStatus, amount, account.getCurrency(),
                                                                     ptmd.getId(), null, e.getMessage(), callContext);
        } finally {
        }

        final DirectPaymentTransaction transaction = new DefaultDirectPaymentTransaction(ptmd.getId(), utcNow, utcNow, pmd.getId(), ptmd.getTransactionType(), utcNow, 0,
                                                                                         paymentStatus, amount, account.getCurrency(),
                                                                                         ((infoPlugin != null) ? infoPlugin.getGatewayErrorCode() : null),
                                                                                         ((infoPlugin != null) ? infoPlugin.getGatewayError() : null),
                                                                                         infoPlugin);
        final List<DirectPaymentTransaction> transactions = Collections.singletonList(transaction);
        final DirectPayment result = new DefaultDirectPayment(inserted.getId(), utcNow, utcNow, account.getId(), account.getPaymentMethodId(), inserted.getPaymentNumber(), externalKey, transactions);
        return result;
    }

    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Iterable<PluginProperty> properties, final InternalCallContext callContext) throws PaymentApiException {
        return null;
    }

    public DirectPayment createPurchase(final Account account, final BigDecimal amount, final String externalKey, final Iterable<PluginProperty> properties, final InternalCallContext callContext) throws PaymentApiException {
        return null;
    }

    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final Iterable<PluginProperty> properties, final InternalCallContext callContext) throws PaymentApiException {
        return null;
    }

    public DirectPayment createCredit(final Account account, final UUID directPaymentId, final Iterable<PluginProperty> properties, final InternalCallContext callContext) throws PaymentApiException {
        return null;
    }

    public List<DirectPayment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final InternalTenantContext tenantContext) throws PaymentApiException {

        final List<DirectPaymentModelDao> paymentsModelDao = paymentDao.getDirectPaymentsForAccount(accountId, tenantContext);
        final List<DirectPaymentTransactionModelDao> transactionsModelDao = paymentDao.getDirectTransactionsForAccount(accountId, tenantContext);

        final Iterable<DirectPayment> payments = Iterables.transform(paymentsModelDao, new Function<DirectPaymentModelDao, DirectPayment>() {

            @Override
            public DirectPayment apply(final DirectPaymentModelDao curDirectPaymentModelDao) {
                return toDirectPayment(curDirectPaymentModelDao, transactionsModelDao);
            }

        });
        return ImmutableList.copyOf(payments);
    }

    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final InternalTenantContext tenantContext) throws PaymentApiException {
        final DirectPaymentModelDao paymentModelDao = paymentDao.getDirectPayment(directPaymentId, tenantContext);

        final InternalTenantContext tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        final List<DirectPaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(paymentModelDao.getAccountId(), tenantContextWithAccountRecordId);

        return toDirectPayment(paymentModelDao, transactionsForAccount);
    }

    private DirectPayment toDirectPayment(final DirectPaymentModelDao curDirectPaymentModelDao, final List<DirectPaymentTransactionModelDao> transactionsModelDao) {

        final Ordering<DirectPaymentTransaction> perPaymentTransactionOrdering = Ordering.<DirectPaymentTransaction>from(new Comparator<DirectPaymentTransaction>() {
            @Override
            public int compare(final DirectPaymentTransaction o1, final DirectPaymentTransaction o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });

        final Iterable<DirectPaymentTransactionModelDao> filteredTransactions = Iterables.filter(transactionsModelDao, new Predicate<DirectPaymentTransactionModelDao>() {
            @Override
            public boolean apply(final DirectPaymentTransactionModelDao curDirectPaymentTransactionModelDao) {
                return curDirectPaymentTransactionModelDao.getDirectPaymentId().equals(curDirectPaymentModelDao.getId());
            }
        });

        final Iterable<DirectPaymentTransaction> transactions = Iterables.transform(filteredTransactions, new Function<DirectPaymentTransactionModelDao, DirectPaymentTransaction>() {
            @Override
            public DirectPaymentTransaction apply(final DirectPaymentTransactionModelDao input) {
                return new DefaultDirectPaymentTransaction(input.getId(), input.getCreatedDate(), input.getUpdatedDate(), input.getDirectPaymentId(),
                                                           input.getTransactionType(), input.getEffectiveDate(), 0, input.getPaymentStatus(), input.getAmount(), input.getCurrency(),
                                                           // STEPH_DP fill in details plugin info if required
                                                           input.getGatewayErrorCode(), input.getGatewayErrorMsg(), null);
            }
        });

        final List<DirectPaymentTransaction> sortedTransactions = perPaymentTransactionOrdering.immutableSortedCopy(transactions);
        return new DefaultDirectPayment(curDirectPaymentModelDao.getId(), curDirectPaymentModelDao.getCreatedDate(), curDirectPaymentModelDao.getUpdatedDate(), curDirectPaymentModelDao.getAccountId(),
                                        curDirectPaymentModelDao.getPaymentMethodId(), curDirectPaymentModelDao.getPaymentNumber(), curDirectPaymentModelDao.getExternalKey(), sortedTransactions);
    }
}
