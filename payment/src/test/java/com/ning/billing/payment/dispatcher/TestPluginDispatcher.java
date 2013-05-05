package com.ning.billing.payment.dispatcher;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.payment.PaymentTestSuiteNoDB;
import com.ning.billing.payment.api.PaymentApiException;

public class TestPluginDispatcher extends PaymentTestSuiteNoDB {

    private final PluginDispatcher<Void> voidPluginDispatcher = new PluginDispatcher<Void>(10, Executors.newSingleThreadExecutor());

    @Test(groups = "fast")
    public void testDispatchWithTimeout() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithAccountLockAndTimeout(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Thread.sleep(1000);
                    return null;
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (TimeoutException e) {
            gotIt = true;
        } catch (PaymentApiException e) {
            Assert.fail("Failed : should have had Timeout exception");
        }
        Assert.assertTrue(gotIt);
    }

    @Test(groups = "fast")
    public void testDispatchWithPaymentApiException() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithAccountLockAndTimeout(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, "foo", "foo");
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (TimeoutException e) {
            Assert.fail("Failed : should have had PaymentApiException exception");
        } catch (PaymentApiException e) {
            gotIt = true;
        }
        Assert.assertTrue(gotIt);
    }

    @Test(groups = "fast")
    public void testDispatchWithRuntimeExceptionWrappedInPaymentApiException() throws TimeoutException, PaymentApiException {
        boolean gotIt = false;
        try {
            voidPluginDispatcher.dispatchWithAccountLockAndTimeout(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    throw new RuntimeException("whatever");
                }
            }, 100, TimeUnit.MILLISECONDS);
            Assert.fail("Failed : should have had Timeout exception");
        } catch (TimeoutException e) {
            Assert.fail("Failed : should have had RuntimeException exception");
        } catch (PaymentApiException e) {
            gotIt = true;
        } catch (RuntimeException e) {
        }
        Assert.assertTrue(gotIt);
    }
}
