/* 
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
package com.ning.billing.payment.dispatcher;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.payment.api.PaymentApiException;

public class PluginDispatcher<T> {
    
    private static final Logger log = LoggerFactory.getLogger(PluginDispatcher.class);
    
    private final long DEFAULT_PLUGIN_TIMEOUT_SEC = 30;
    private final TimeUnit DEEFAULT_PLUGIN_TIMEOUT_UNIT = TimeUnit.SECONDS;
    
    private final ExecutorService executor;
    
    public PluginDispatcher(ExecutorService executor) {
        this.executor = executor;
    }
    
    public T dispatchWithAccountLock(Callable<T> task) 
        throws PaymentApiException, TimeoutException {
        return dispatchWithAccountLockAndTimeout(task, DEFAULT_PLUGIN_TIMEOUT_SEC, DEEFAULT_PLUGIN_TIMEOUT_UNIT);
    }
    
    public T dispatchWithAccountLockAndTimeout(Callable<T> task, long timeout, TimeUnit unit)
    throws PaymentApiException, TimeoutException  {

        try {
            Future<T> future = executor.submit(task);
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
        } 
    }
    
  
}
