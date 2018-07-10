/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.ProcessorBase.DispatcherCallback;
import org.killbill.billing.payment.core.sm.payments.PaymentOperation;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.awaitility.Awaitility;

public class TestPluginOperation extends PaymentTestSuiteNoDB {

    private static final String PLUGIN_NAME_PLACEHOLDER = "pluginName";

    private static final int TIMEOUT = 10;

    private final GlobalLocker locker = new MemoryGlobalLocker();
    private final Account account = Mockito.mock(Account.class);

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false",
                                                               "org.killbill.payment.globalLock.retries", "1"));

    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
    }

    @Test(groups = "fast")
    public void testAccountLock() throws Exception {
        testLocking();
    }

    @Test(groups = "fast")
    public void testOperationThrowsPaymentApiException() throws Exception {
        final CallbackTest callback = new CallbackTest(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE));
        final PaymentOperation pluginOperation = getPluginOperation();

        try {
            pluginOperation.dispatchWithAccountLockAndTimeout(PLUGIN_NAME_PLACEHOLDER, callback);
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.EXCEPTION);
            Assert.assertTrue(e.getCause() instanceof PaymentApiException);
        }
    }

    @Test(groups = "fast")
    public void testOperationThrowsRuntimeException() throws Exception {
        final CallbackTest callback = new CallbackTest(new NullPointerException("Expected for the test"));
        final PaymentOperation pluginOperation = getPluginOperation();

        try {
            pluginOperation.dispatchWithAccountLockAndTimeout(PLUGIN_NAME_PLACEHOLDER, callback);
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.EXCEPTION);
            Assert.assertTrue(e.getCause() instanceof PaymentApiException);
            Assert.assertTrue(e.getCause().getCause() instanceof NullPointerException);
        }
    }

    private void testLocking() throws Exception {
        final Semaphore available = new Semaphore(1, true);
        final CallbackTest callback = new CallbackTest(available);
        final PaymentOperation pluginOperation = getPluginOperation(true);

        // Take the only permit
        available.acquire();

        // Start the plugin operation in the background (will block)
        runPluginOperationInBackground(pluginOperation, callback, false);
        Awaitility.await()
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callback.getStartCount() == 1;
                      }
                  });

        // The operation should be blocked here because we have the semaphore
        Assert.assertEquals(callback.getRunCount(), 0);

        // Trying to run the operation again will throw LockFailedException
        Awaitility.await().atMost(2 * TIMEOUT, TimeUnit.SECONDS).untilTrue(runPluginOperationInBackground(pluginOperation, callback, true));

        Assert.assertEquals(callback.getRunCount(), 0);

        // Release the semaphore
        available.release();

        // Give some time for the operation to run
        Awaitility.await()
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callback.getRunCount() == 1;
                      }
                  });

        // Verify the final state
        Assert.assertEquals(available.availablePermits(), 1);
    }

    private AtomicBoolean runPluginOperationInBackground(final PaymentOperation pluginOperation, final CallbackTest callback, final boolean shouldFailBecauseOfLockFailure) throws Exception {
        final AtomicBoolean threadRunning = new AtomicBoolean(false);
        final AtomicBoolean threadHasRun = new AtomicBoolean(false);
        final Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                threadRunning.set(true);

                try {
                    if (shouldFailBecauseOfLockFailure) {
                        try {
                            pluginOperation.dispatchWithAccountLockAndTimeout(PLUGIN_NAME_PLACEHOLDER, callback);
                            Assert.fail();
                        } catch (final OperationException e) {
                            Assert.assertTrue(e.getCause() instanceof PaymentApiException);
                            // No better error code for lock failures...
                            Assert.assertEquals(((PaymentApiException) e.getCause()).getCode(), ErrorCode.PAYMENT_INTERNAL_ERROR.getCode());
                        }
                    } else {
                        try {
                            pluginOperation.dispatchWithAccountLockAndTimeout(PLUGIN_NAME_PLACEHOLDER, callback);
                        } catch (final OperationException e) {
                            Assert.fail(e.getMessage());
                        }
                    }
                } finally {
                    threadHasRun.set(true);
                }
            }
        });

        t1.start();

        // Make sure the thread has started
        Awaitility.await().untilTrue(threadRunning);

        return threadHasRun;
    }

    private PaymentOperation getPluginOperation() throws PaymentApiException {
        return getPluginOperation(false);
    }

    private PaymentOperation getPluginOperation(final boolean shouldLockAccount) throws PaymentApiException {
        return getPluginOperation(shouldLockAccount, TIMEOUT);
    }

    private PaymentOperation getPluginOperation(final boolean shouldLockAccount, final int timeoutSeconds) throws PaymentApiException {
        final PluginDispatcher<OperationResult> paymentPluginDispatcher = new PluginDispatcher<OperationResult>(timeoutSeconds, paymentExecutors);

        final PaymentStateContext paymentStateContext = new PaymentStateContext(true,
                                                                                UUID.randomUUID(),
                                                                                null,
                                                                                null,
                                                                                UUID.randomUUID().toString(),
                                                                                UUID.randomUUID().toString(),
                                                                                TransactionType.CAPTURE,
                                                                                account,
                                                                                UUID.randomUUID(),
                                                                                new BigDecimal("192.3920111"),
                                                                                Currency.BRL,
                                                                                null,
                                                                                null,
                                                                                null,
                                                                                shouldLockAccount,
                                                                                null,
                                                                                ImmutableList.<PluginProperty>of(),
                                                                                internalCallContext,
                                                                                callContext);

        final PaymentAutomatonDAOHelper daoHelper = Mockito.mock(PaymentAutomatonDAOHelper.class);
        Mockito.when(daoHelper.getPaymentPluginApi()).thenReturn(null);
        return new PluginOperationTest(daoHelper, locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
    }

    private static final class CallbackTest implements DispatcherCallback<PluginDispatcherReturnType<OperationResult>, PaymentApiException> {

        private final AtomicInteger startCount = new AtomicInteger(0);
        private final AtomicInteger runCount = new AtomicInteger(0);

        private final Semaphore available;
        private final Integer sleepTimeMillis;
        private final PaymentApiException paymentApiException;
        private final RuntimeException runtimeException;

        public CallbackTest(final Semaphore available) {
            this(available, null, null, null);
        }

        public CallbackTest(final Integer sleepTimeMillis) {
            this(null, sleepTimeMillis, null, null);
        }

        public CallbackTest(final PaymentApiException paymentApiException) {
            this(null, null, paymentApiException, null);
        }

        public CallbackTest(final RuntimeException runtimeException) {
            this(null, null, null, runtimeException);
        }

        private CallbackTest(@Nullable final Semaphore available, @Nullable final Integer sleepTimeMillis,
                             @Nullable final PaymentApiException paymentApiException, @Nullable final RuntimeException runtimeException) {
            this.available = available;
            this.sleepTimeMillis = sleepTimeMillis;
            this.paymentApiException = paymentApiException;
            this.runtimeException = runtimeException;
        }

        @Override
        public PluginDispatcherReturnType<OperationResult> doOperation() throws PaymentApiException {
            startCount.incrementAndGet();

            try {
                if (available != null) {
                    available.acquireUninterruptibly();
                }

                if (sleepTimeMillis != null) {
                    Thread.sleep(sleepTimeMillis);
                }

                if (paymentApiException != null) {
                    throw paymentApiException;
                } else if (runtimeException != null) {
                    throw runtimeException;
                } else {
                    runCount.incrementAndGet();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                Assert.fail(e.getMessage());
            } finally {
                if (available != null) {
                    available.release();
                }
            }
            return PluginDispatcher.createPluginDispatcherReturnType(null);
        }

        public int getRunCount() {
            return runCount.get();
        }

        public int getStartCount() {
            return startCount.get();
        }
    }

    private static final class PluginOperationTest extends PaymentOperation {

        protected PluginOperationTest(final PaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final PaymentConfig paymentConfig, final PaymentStateContext paymentStateContext) throws PaymentApiException {
            super(locker, daoHelper, paymentPluginDispatcher, paymentConfig, paymentStateContext);
        }

        @Override
        protected PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException {
            return null;
        }
    }
}
