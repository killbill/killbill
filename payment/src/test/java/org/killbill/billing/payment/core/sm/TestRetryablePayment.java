/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner.ControlOperation;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestRetryablePayment extends PaymentTestSuiteNoDB {

    @Inject
    @Named(PaymentModule.STATE_MACHINE_RETRY)
    private StateMachineConfig retryStateMachineConfig;
    @Inject
    private PaymentDao paymentDao;
    @Inject
    private NonEntityDao nonEntityDao;
    @Inject
    private GlobalLocker locker;
    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry;
    @Inject
    private TagInternalApi tagApi;
    @Inject
    private PaymentProcessor paymentProcessor;
    @Inject
    @Named(RETRYABLE_NAMED)
    private RetryServiceScheduler retryServiceScheduler;
    @Inject
    private PaymentExecutors executors;
    @Inject
    private PaymentStateMachineHelper paymentSMHelper;
    @Inject
    private PaymentControlStateMachineHelper retrySMHelper;
    @Inject
    private ControlPluginRunner controlPluginRunner;
    @Inject
    private InternalCallContextFactory internalCallContextFactory;

    private Account account;
    private DateTime utcNow;

    private final UUID paymentMethodId = UUID.randomUUID();
    private final String paymentExternalKey = "foo";
    private final String paymentTransactionExternalKey = "foobar";
    private final BigDecimal amount = BigDecimal.ONE;
    private final Currency currency = Currency.EUR;
    private final ImmutableList<PluginProperty> emptyProperties = ImmutableList.of();
    private final MockPaymentControlProviderPlugin mockRetryProviderPlugin = new MockPaymentControlProviderPlugin();

    private byte[] EMPTY_PROPERTIES;
    private MockRetryablePaymentAutomatonRunner runner;
    private PaymentStateControlContext paymentStateContext;
    private MockRetryAuthorizeOperationCallback mockRetryAuthorizeOperationCallback;
    private PluginControlPaymentProcessor processor;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        account = testHelper.createTestAccount("lolo@gmail.com", false);
        Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        //Mockito.when(nonEntityDao.retrieveIdFromObject(Mockito.<Long>any(), Mockito.<ObjectType>any())).thenReturn(uuid);
        retryPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return MockPaymentControlProviderPlugin.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return MockPaymentControlProviderPlugin.PLUGIN_NAME;
            }
        }, mockRetryProviderPlugin);
        EMPTY_PROPERTIES = PluginPropertySerializer.serialize(ImmutableList.<PluginProperty>of());
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        this.utcNow = clock.getUTCNow();

        runner = new MockRetryablePaymentAutomatonRunner(
                paymentDao,
                locker,
                paymentPluginServiceRegistration,
                retryPluginRegistry,
                clock,
                tagApi,
                paymentProcessor,
                retryServiceScheduler,
                paymentConfig,
                paymentExecutors,
                paymentSMHelper,
                retrySMHelper,
                controlPluginRunner,
                eventBus,
                paymentRefresher);

        paymentStateContext =
                new PaymentStateControlContext(ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME),
                                               true,
                                               null,
                                               null,
                                               paymentExternalKey,
                                               null,
                                               paymentTransactionExternalKey,
                                               TransactionType.AUTHORIZE,
                                               account,
                                               paymentMethodId,
                                               amount,
                                               currency,
                                               null,
                                               emptyProperties,
                                               internalCallContext,
                                               callContext);

        mockRetryAuthorizeOperationCallback =
                new MockRetryAuthorizeOperationCallback(locker,
                                                        runner.getPaymentPluginDispatcher(),
                                                        paymentConfig,
                                                        paymentStateContext,
                                                        null,
                                                        controlPluginRunner,
                                                        paymentDao,
                                                        clock);

        processor = new PluginControlPaymentProcessor(paymentPluginServiceRegistration,
                                                      accountInternalApi,
                                                      null,
                                                      tagApi,
                                                      paymentDao,
                                                      locker,
                                                      internalCallContextFactory,
                                                      runner,
                                                      retrySMHelper,
                                                      clock
        );

    }

    @Test(groups = "fast")
    public void testInitToAborted() throws PaymentApiException {

        mockRetryProviderPlugin
                .setAborted(true)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.SUCCESS)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext,
                       internalCallContext);
            fail();
        } catch (PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode());
        }
        assertFalse(mockRetryProviderPlugin.isOnSuccessCallExecuted(), "OnSuccessCall method should not be called when payment is aborted");
        assertFalse(mockRetryProviderPlugin.isOnFailureCallExecuted(), "onFailureCall method should not be called when payment is aborted");

        final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
        assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "ABORTED");
        assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithResSuccess() throws PaymentApiException {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.SUCCESS)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        runner.run(true,
                   TransactionType.AUTHORIZE,
                   ControlOperation.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   paymentExternalKey,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   null,
                   emptyProperties,
                   null,
                   callContext,
                   internalCallContext);

        final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
        assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithResFailure() throws PaymentApiException {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.FAILURE)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        runner.run(true,
                   TransactionType.AUTHORIZE,
                   ControlOperation.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   paymentExternalKey,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   null,
                   emptyProperties,
                   null,
                   callContext, internalCallContext);

        final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
        assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithPaymentExceptionNoRetries() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE, "bla bla"));

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            fail("Expected PaymentApiException...");

        } catch (final PaymentApiException e) {
            final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
            assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
        }
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithPaymentExceptionAndRetries() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(new DateTime(clock.getUTCNow().plusDays(1)));

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE, "bla bla"));

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            fail("Expected PaymentApiException...");
        } catch (final PaymentApiException e) {
            final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
            assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
        }
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithRuntimeExceptionAndRetries() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(new DateTime(clock.getUTCNow().plusDays(1)));

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new RuntimeException());

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            fail("Expected Exception...");
        } catch (final PaymentApiException e) {
            final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
            assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
        }
    }

    @Test(groups = "fast")
    public void testInitToSuccessWithRuntimeExceptionNoRetry() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new RuntimeException());

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            fail("Expected Exception...");
        } catch (final PaymentApiException e) {
            final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
            assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
        }
    }

    @Test(groups = "fast")
    public void testRetryToSuccessWithResSuccess() throws PaymentApiException {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.SUCCESS)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        final State state = retrySMHelper.getRetriedState();
        final UUID transactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                                 paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                                 TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES),
                                                      internalCallContext
                                                     );
        runner.run(state,
                   false,
                   TransactionType.AUTHORIZE,
                   ControlOperation.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   paymentExternalKey,
                   paymentTransactionExternalKey,
                   amount,
                   currency,
                   null,
                   emptyProperties,
                   null,
                   callContext,
                   internalCallContext);

        final List<PaymentAttemptModelDao> pas = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);
        assertEquals(pas.size(), 2);
        final PaymentAttemptModelDao successfulAttempt = Iterables.tryFind(pas, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(final PaymentAttemptModelDao input) {
                return input.getTransactionType() == TransactionType.AUTHORIZE &&
                       input.getStateName().equals("SUCCESS");
            }
        }).orNull();
        assertNotNull(successfulAttempt);
    }

    @Test(groups = "fast")
    public void testRetryToSuccessWithPaymentApiExceptionAndRetry() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(new DateTime().plusDays(1));

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE, "bla"));

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        final State state = retrySMHelper.getRetriedState();
        final UUID transactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                                 paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                                 TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES),
                                                      internalCallContext
                                                     );

        try {
            runner.run(state,
                       false,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext,
                       internalCallContext);

            fail("Expecting paymentApiException...");
        } catch (final PaymentApiException e) {
            final PaymentAttemptModelDao pa = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext).get(0);
            assertEquals(pa.getTransactionExternalKey(), paymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getTransactionType(), TransactionType.AUTHORIZE);
        }
    }

    @Test(groups = "fast")
    public void testRetryToSuccessWithPaymentApiExceptionAndNoRetry() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE, "bla"));

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        final State state = retrySMHelper.getRetriedState();
        final UUID transactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                                 paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                                 TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES),
                                                      internalCallContext
                                                     );

        try {
            runner.run(state,
                       false,
                       TransactionType.AUTHORIZE,
                       ControlOperation.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       paymentExternalKey,
                       paymentTransactionExternalKey,
                       amount,
                       currency,
                       null,
                       emptyProperties,
                       null,
                       callContext,
                       internalCallContext);

            fail("Expecting paymentApiException...");
        } catch (final PaymentApiException e) {

            final List<PaymentAttemptModelDao> pas = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);
            assertEquals(pas.size(), 2);

            final PaymentAttemptModelDao failedAttempts = Iterables.tryFind(pas, new Predicate<PaymentAttemptModelDao>() {
                @Override
                public boolean apply(final PaymentAttemptModelDao input) {
                    return input.getTransactionType() == TransactionType.AUTHORIZE &&
                           input.getStateName().equals("ABORTED");
                }
            }).orNull();
            assertNotNull(failedAttempts);
        }
    }

    @Test(groups = "fast")
    public void testRetryLogicFromRetriedStateWithSuccess() throws PaymentApiException {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(OperationResult.SUCCESS)
                .setException(null);

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        final State state = retrySMHelper.getRetriedState();
        final UUID transactionId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                          paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                          TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES);
        paymentDao.insertPaymentAttemptWithProperties(attempt,
                                                      internalCallContext
                                                     );
        paymentDao.insertPaymentWithFirstTransaction(new PaymentModelDao(paymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, paymentExternalKey),
                                                     new PaymentTransactionModelDao(transactionId, attempt.getId(), paymentTransactionExternalKey, utcNow, utcNow, paymentId, TransactionType.AUTHORIZE, utcNow, TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                     internalCallContext);

        processor.retryPaymentTransaction(attempt.getId(), ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME), internalCallContext);

        final List<PaymentAttemptModelDao> pas = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);
        assertEquals(pas.size(), 2);

        final PaymentAttemptModelDao successfulAttempt = Iterables.tryFind(pas, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(final PaymentAttemptModelDao input) {
                return input.getTransactionType() == TransactionType.AUTHORIZE &&
                       input.getStateName().equals("SUCCESS");
            }
        }).orNull();
        assertNotNull(successfulAttempt);
    }

    @Test(groups = "fast")
    public void testRetryLogicFromRetriedStateWithPaymentApiException() {

        mockRetryProviderPlugin
                .setAborted(false)
                .setNextRetryDate(null);

        mockRetryAuthorizeOperationCallback
                .setResult(null)
                .setException(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE, "foo"));

        runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
              .setContext(paymentStateContext);

        final State state = retrySMHelper.getRetriedState();
        final UUID transactionId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                          paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                          TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES);
        paymentDao.insertPaymentAttemptWithProperties(attempt,
                                                      internalCallContext
                                                     );
        paymentDao.insertPaymentWithFirstTransaction(new PaymentModelDao(paymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, paymentExternalKey),
                                                     new PaymentTransactionModelDao(transactionId, attempt.getId(), paymentTransactionExternalKey, utcNow, utcNow, paymentId, TransactionType.AUTHORIZE, utcNow,
                                                                                    TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                     internalCallContext
                                                    );

        processor.retryPaymentTransaction(attempt.getId(), ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME), internalCallContext);

        final List<PaymentAttemptModelDao> pas = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);
        assertEquals(pas.size(), 2);
        final PaymentAttemptModelDao failedAttempt = Iterables.tryFind(pas, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(final PaymentAttemptModelDao input) {
                return input.getTransactionType() == TransactionType.AUTHORIZE &&
                       input.getStateName().equals("ABORTED");
            }
        }).orNull();
        assertNotNull(failedAttempt);
    }

    @Test(groups = "fast")
    public void testRetryLogicFromRetriedStateWithLockFailure() throws LockFailedException {

        GlobalLock lock = null;
        try {
            // Grab lock so that operation later will fail...
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), account.getId().toString(), 1);

            mockRetryProviderPlugin
                    .setAborted(false)
                    .setNextRetryDate(null);

            mockRetryAuthorizeOperationCallback
                    .setResult(OperationResult.SUCCESS)
                    .setException(null);

            runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
                  .setContext(paymentStateContext);

            final State state = retrySMHelper.getRetriedState();
            final UUID transactionId = UUID.randomUUID();
            final UUID paymentId = UUID.randomUUID();
            final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), paymentMethodId, utcNow, utcNow,
                                                                              paymentExternalKey, transactionId, paymentTransactionExternalKey,
                                                                              TransactionType.AUTHORIZE, state.getName(), amount, currency, null, EMPTY_PROPERTIES);
            paymentDao.insertPaymentAttemptWithProperties(attempt,
                                                          internalCallContext
                                                         );
            paymentDao.insertPaymentWithFirstTransaction(new PaymentModelDao(paymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, paymentExternalKey),
                                                         new PaymentTransactionModelDao(transactionId, attempt.getId(), paymentTransactionExternalKey, utcNow, utcNow, paymentId, TransactionType.AUTHORIZE, utcNow,
                                                                                        TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                         internalCallContext
                                                        );

            processor.retryPaymentTransaction(attempt.getId(), ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME), internalCallContext);

            final List<PaymentAttemptModelDao> pas = paymentDao.getPaymentAttemptByTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);
            assertEquals(pas.size(), 2);
            final PaymentAttemptModelDao failedAttempt = Iterables.tryFind(pas, new Predicate<PaymentAttemptModelDao>() {
                @Override
                public boolean apply(final PaymentAttemptModelDao input) {
                    return input.getTransactionType() == TransactionType.AUTHORIZE &&
                           input.getStateName().equals("ABORTED");
                }
            }).orNull();
            assertNotNull(failedAttempt);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }
}
