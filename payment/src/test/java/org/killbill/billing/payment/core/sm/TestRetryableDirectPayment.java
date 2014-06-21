/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PluginControlledPaymentProcessor;
import org.killbill.billing.payment.dao.MockPaymentDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertyModelDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.payment.glue.PaymentModule.RETRYABLE_NAMED;
import static org.testng.Assert.assertEquals;

public class TestRetryableDirectPayment extends PaymentTestSuiteNoDB {

    @Inject
    @Named(PaymentModule.STATE_MACHINE_PAYMENT)
    private StateMachineConfig stateMachineConfig;
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
    private DirectPaymentProcessor directPaymentProcessor;
    @Inject
    @Named(RETRYABLE_NAMED)
    private RetryServiceScheduler retryServiceScheduler;
    @Inject
    @Named(PLUGIN_EXECUTOR_NAMED)
    private ExecutorService executor;

    private Account account;
    private DateTime utcNow;

    private final UUID paymentMethodId = UUID.randomUUID();
    private final String directPaymentExternalKey = "foo";
    private final String directPaymentTransactionExternalKey = "foobar";
    private final BigDecimal amount = BigDecimal.ONE;
    private final Currency currency = Currency.EUR;
    private final ImmutableList<PluginProperty> emptyProperties = ImmutableList.<PluginProperty>of();
    private final MockPaymentControlProviderPlugin mockRetryProviderPlugin = new MockPaymentControlProviderPlugin();

    private MockRetryableDirectPaymentAutomatonRunner runner;
    private RetryableDirectPaymentStateContext directPaymentStateContext;
    private MockRetryAuthorizeOperationCallback mockRetryAuthorizeOperationCallback;
    private PluginControlledPaymentProcessor processor;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        account = testHelper.createTestAccount("lolo@gmail.com", false);
        Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        final UUID uuid = UUID.randomUUID();
        //Mockito.when(nonEntityDao.retrieveIdFromObject(Mockito.<Long>any(), Mockito.<ObjectType>any())).thenReturn(uuid);
        retryPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getRegistrationName() {
                return MockPaymentControlProviderPlugin.PLUGIN_NAME;
            }
        }, mockRetryProviderPlugin);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        ((MockPaymentDao) paymentDao).reset();
        this.utcNow = clock.getUTCNow();

        runner = new MockRetryableDirectPaymentAutomatonRunner(
                stateMachineConfig,
                retryStateMachineConfig,
                paymentDao,
                locker,
                pluginRegistry,
                retryPluginRegistry,
                clock,
                tagApi,
                directPaymentProcessor,
                retryServiceScheduler,
                paymentConfig,
                executor);

        directPaymentStateContext =
                new RetryableDirectPaymentStateContext(MockPaymentControlProviderPlugin.PLUGIN_NAME,
                                                       true,
                                                       null,
                                                       directPaymentExternalKey,
                                                       directPaymentTransactionExternalKey,
                                                       TransactionType.AUTHORIZE,
                                                       account,
                                                       paymentMethodId,
                                                       amount,
                                                       currency,
                                                       emptyProperties,
                                                       internalCallContext,
                                                       callContext);

        mockRetryAuthorizeOperationCallback =
                new MockRetryAuthorizeOperationCallback(locker,
                                                        runner.getPaymentPluginDispatcher(),
                                                        directPaymentStateContext,
                                                        null,
                                                        runner.getRetryPluginRegistry(),
                                                        paymentDao,
                                                        clock);

        processor = new PluginControlledPaymentProcessor(pluginRegistry,
                                                         accountInternalApi,
                                                         null,
                                                         tagApi,
                                                         paymentDao,
                                                         nonEntityDao,
                                                         eventBus,
                                                         locker,
                                                         executor,
                                                         runner,
                                                         clock);

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
              .setContext(directPaymentStateContext);

        runner.run(true,
                   TransactionType.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   directPaymentExternalKey,
                   directPaymentTransactionExternalKey,
                   amount,
                   currency,
                   emptyProperties,
                   null,
                   callContext,
                   internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "ABORTED");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        runner.run(true,
                   TransactionType.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   directPaymentExternalKey,
                   directPaymentTransactionExternalKey,
                   amount,
                   currency,
                   emptyProperties,
                   null,
                   callContext,
                   internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        runner.run(true,
                   TransactionType.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   directPaymentExternalKey,
                   directPaymentTransactionExternalKey,
                   amount,
                   currency,
                   emptyProperties,
                   null,
                   callContext, internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            Assert.fail("Expected PaymentApiException...");

        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            Assert.fail("Expected PaymentApiException...");
        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            Assert.fail("Expected Exception...");
        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        try {
            runner.run(true,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext, internalCallContext);

            Assert.fail("Expected Exception...");
        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        final State state = runner.fetchState("RETRIED");
        final UUID directTransactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                      ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);
        runner.run(state,
                   false,
                   TransactionType.AUTHORIZE,
                   account,
                   paymentMethodId,
                   null,
                   directPaymentExternalKey,
                   directPaymentTransactionExternalKey,
                   amount,
                   currency,
                   emptyProperties,
                   null,
                   callContext,
                   internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        final State state = runner.fetchState("RETRIED");
        final UUID directTransactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                      ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);

        try {
            runner.run(state,
                       false,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext,
                       internalCallContext);

            Assert.fail("Expecting paymentApiException...");
        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "RETRIED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        final State state = runner.fetchState("RETRIED");
        final UUID directTransactionId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                      ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);

        try {
            runner.run(state,
                       false,
                       TransactionType.AUTHORIZE,
                       account,
                       paymentMethodId,
                       null,
                       directPaymentExternalKey,
                       directPaymentTransactionExternalKey,
                       amount,
                       currency,
                       emptyProperties,
                       null,
                       callContext,
                       internalCallContext);

            Assert.fail("Expecting paymentApiException...");
        } catch (PaymentApiException e) {
            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        final State state = runner.fetchState("RETRIED");
        final UUID directTransactionId = UUID.randomUUID();
        final UUID directPaymentId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                      ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);
        paymentDao.insertDirectPaymentWithFirstTransaction(new PaymentModelDao(directPaymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, directPaymentExternalKey),
                                                           new PaymentTransactionModelDao(directTransactionId, directPaymentTransactionExternalKey, utcNow, utcNow, directPaymentId, TransactionType.AUTHORIZE, utcNow, TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                           internalCallContext);

        processor.retryPaymentTransaction(directPaymentTransactionExternalKey, MockPaymentControlProviderPlugin.PLUGIN_NAME, internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "SUCCESS");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
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
              .setContext(directPaymentStateContext);

        final State state = runner.fetchState("RETRIED");
        final UUID directTransactionId = UUID.randomUUID();
        final UUID directPaymentId = UUID.randomUUID();
        paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                      ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);
        paymentDao.insertDirectPaymentWithFirstTransaction(new PaymentModelDao(directPaymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, directPaymentExternalKey),
                                                           new PaymentTransactionModelDao(directTransactionId, directPaymentTransactionExternalKey, utcNow, utcNow, directPaymentId, TransactionType.AUTHORIZE, utcNow,
                                                                                          TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                           internalCallContext
                                                          );

        processor.retryPaymentTransaction(directPaymentTransactionExternalKey, MockPaymentControlProviderPlugin.PLUGIN_NAME, internalCallContext);

        final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
        assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
        assertEquals(pa.getStateName(), "ABORTED");
        assertEquals(pa.getOperationName(), "AUTHORIZE");
    }

    @Test(groups = "fast")
    public void testRetryLogicFromRetriedStateWithLockFailure() throws LockFailedException {

        GlobalLock lock = null;
        try {
            // Grab lock so that operation later will fail...
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), account.getExternalKey(), 1);

            mockRetryProviderPlugin
                    .setAborted(false)
                    .setNextRetryDate(null);

            mockRetryAuthorizeOperationCallback
                    .setResult(OperationResult.SUCCESS)
                    .setException(null);

            runner.setOperationCallback(mockRetryAuthorizeOperationCallback)
                  .setContext(directPaymentStateContext);

            final State state = runner.fetchState("RETRIED");
            final UUID directTransactionId = UUID.randomUUID();
            final UUID directPaymentId = UUID.randomUUID();
            paymentDao.insertPaymentAttemptWithProperties(new PaymentAttemptModelDao(utcNow, utcNow, directPaymentExternalKey, directTransactionId, directPaymentTransactionExternalKey, state.getName(), TransactionType.AUTHORIZE.name(), null),
                                                          ImmutableList.<PluginPropertyModelDao>of(), internalCallContext);
            paymentDao.insertDirectPaymentWithFirstTransaction(new PaymentModelDao(directPaymentId, utcNow, utcNow, account.getId(), paymentMethodId, -1, directPaymentExternalKey),
                                                               new PaymentTransactionModelDao(directTransactionId, directPaymentTransactionExternalKey, utcNow, utcNow, directPaymentId, TransactionType.AUTHORIZE, utcNow,
                                                                                              TransactionStatus.PAYMENT_FAILURE, amount, currency, "bla", "foo"),
                                                               internalCallContext
                                                              );

            processor.retryPaymentTransaction(directPaymentTransactionExternalKey, MockPaymentControlProviderPlugin.PLUGIN_NAME, internalCallContext);

            final PaymentAttemptModelDao pa = ((MockPaymentDao) paymentDao).getPaymentAttemptByExternalKey(directPaymentTransactionExternalKey, internalCallContext);
            assertEquals(pa.getTransactionExternalKey(), directPaymentTransactionExternalKey);
            assertEquals(pa.getStateName(), "ABORTED");
            assertEquals(pa.getOperationName(), "AUTHORIZE");

        } finally {
            if (lock != null) {
                lock.release();
            }
        }

    }

}