/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.platform.profiling.Profiling;
import org.killbill.billing.platform.profiling.ProfilingData.ProfilingDataOutput;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.jayway.awaitility.Awaitility;

public class TestPluginOperation extends PaymentTestSuiteNoDB {

    private final GlobalLocker locker = new MemoryGlobalLocker();
    private final Account account = Mockito.mock(Account.class);

    private static final Logger logger = LoggerFactory.getLogger(TestPluginOperation.class);

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        Mockito.when(account.getExternalKey()).thenReturn(UUID.randomUUID().toString());
    }

    @Test(groups = "fast")
    public void testWithAccountLock() throws Exception {
        testLocking(true);
    }


    @Test(groups = "fast")
    public void testOperationThrowsPaymentApiException() throws Exception {
        final CallbackTest callback = new CallbackTest(new PaymentApiException(ErrorCode.__UNKNOWN_ERROR_CODE));
        final PaymentOperation pluginOperation = getPluginOperation();

        try {
            pluginOperation.dispatchWithAccountLockAndTimeout(callback);
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.FAILURE);
            Assert.assertTrue(e.getCause() instanceof PaymentApiException);
        }
    }

    @Test(groups = "fast")
    public void testOperationThrowsRuntimeException() throws Exception {
        final CallbackTest callback = new CallbackTest(new NullPointerException("Expected for the test"));
        final PaymentOperation pluginOperation = getPluginOperation();

        try {
            pluginOperation.dispatchWithAccountLockAndTimeout(callback);
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.EXCEPTION);
            Assert.assertTrue(e.getCause() instanceof NullPointerException);
        }
    }

    private void testLocking(final boolean withAccountLock) throws Exception {
        final Semaphore available = new Semaphore(1, true);
        final CallbackTest callback = new CallbackTest(available);
        final PaymentOperation pluginOperation = getPluginOperation(withAccountLock);

        // Take the only permit
        available.acquire();

        // Start the plugin operation in the background (will block)
        runPluginOperationInBackground(pluginOperation, callback, false);

        // The operation should be blocked here because we have the semaphore
        Assert.assertEquals(available.getQueueLength(), 1);
        Assert.assertEquals(callback.getRunCount(), 0);

        if (withAccountLock) {
            // If the account is locked, trying to run the operation again will throw LockFailedException
            runPluginOperationInBackground(pluginOperation, callback, true);
        } else {
            // If the account is not locked, it will just block
            runPluginOperationInBackground(pluginOperation, callback, false);
            Assert.assertEquals(available.getQueueLength(), 2);
            Assert.assertEquals(callback.getRunCount(), 0);
        }

        // Release the semaphore
        available.release();

        // Give some time for the operation to run
        Awaitility.await()
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callback.getRunCount() == (withAccountLock ? 1 : 2);
                      }
                  });

        // Verify the final state
        Assert.assertEquals(available.getQueueLength(), 0);
        Assert.assertEquals(callback.getRunCount(), withAccountLock ? 1 : 2);
    }

    private void runPluginOperationInBackground(final PaymentOperation pluginOperation, final CallbackTest callback, final boolean shouldFailBecauseOfLockFailure) throws Exception {
        final AtomicBoolean threadStarted = new AtomicBoolean(false);
        final Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                threadStarted.set(true);

                if (shouldFailBecauseOfLockFailure) {
                    try {
                        pluginOperation.dispatchWithAccountLockAndTimeout(callback);
                        Assert.fail();
                    } catch (final OperationException e) {
                        Assert.assertTrue(e.getCause() instanceof PaymentApiException);
                        // No better error code for lock failures...
                        Assert.assertEquals(((PaymentApiException) e.getCause()).getCode(), ErrorCode.PAYMENT_INTERNAL_ERROR.getCode());
                    }
                } else {
                    try {
                        pluginOperation.dispatchWithAccountLockAndTimeout(callback);
                    } catch (final OperationException e) {
                        Assert.fail(e.getMessage());
                    }
                }
            }
        });

        t1.start();

        // Make sure the thread has started
        Awaitility.await().untilTrue(threadStarted);
    }

    private PaymentOperation getPluginOperation() throws PaymentApiException {
        return getPluginOperation(false);
    }

    private PaymentOperation getPluginOperation(final boolean shouldLockAccount) throws PaymentApiException {
        return getPluginOperation(shouldLockAccount, Integer.MAX_VALUE);
    }

    private PaymentOperation getPluginOperation(final boolean shouldLockAccount, final int timeoutSeconds) throws PaymentApiException {
        final PluginDispatcher<OperationResult> paymentPluginDispatcher = new PluginDispatcher<OperationResult>(timeoutSeconds, Executors.newCachedThreadPool());

        final PaymentStateContext paymentStateContext = new PaymentStateContext(true, UUID.randomUUID(),
                                                                                null, null,
                                                                                UUID.randomUUID().toString(),
                                                                                UUID.randomUUID().toString(),
                                                                                TransactionType.CAPTURE,
                                                                                account,
                                                                                UUID.randomUUID(),
                                                                                new BigDecimal("192.3920111"),
                                                                                Currency.BRL,
                                                                                shouldLockAccount,
                                                                                null, ImmutableList.<PluginProperty>of(),
                                                                                internalCallContext,
                                                                                callContext);

        final PaymentAutomatonDAOHelper daoHelper = Mockito.mock(PaymentAutomatonDAOHelper.class);
        Mockito.when(daoHelper.getPaymentProviderPlugin()).thenReturn(null);
        return new PluginOperationTest(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
    }

    private static final class CallbackTest implements WithAccountLockCallback<PluginDispatcherReturnType<OperationResult>, PaymentApiException> {

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
            try {
                if (available != null) {
                    available.acquire();
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
    }

    private static final class PluginOperationTest extends PaymentOperation {

        protected PluginOperationTest(final PaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final PaymentStateContext paymentStateContext) throws PaymentApiException {
            super(locker, daoHelper, paymentPluginDispatcher, paymentStateContext);
        }

        @Override
        protected PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException {
            return null;
        }
    }
}
