/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class TestBusinessExecutor extends AnalyticsTestSuiteNoDB {

    @Override
    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        super.setUp();

        logService = Mockito.mock(OSGIKillbillLogService.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                //logger.info(Arrays.toString(invocation.getArguments()));
                return null;
            }
        }).when(logService).log(Mockito.anyInt(), Mockito.anyString());
    }

    @Test(groups = "fast")
    public void testRejectionPolicy() throws Exception {
        final Executor executor = BusinessExecutor.newCachedThreadPool();
        final CompletionService<Integer> completionService = new ExecutorCompletionService<Integer>(executor);

        final int totalTasksSize = BusinessExecutor.NB_THREADS * 50;
        final AtomicInteger taskCounter = new AtomicInteger(totalTasksSize);
        for (int i = 0; i < totalTasksSize; i++) {
            completionService.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    // Sleep a bit to trigger the rejection
                    Thread.sleep(100);
                    taskCounter.getAndDecrement();
                    return 1;
                }
            });
        }

        int results = 0;
        for (int i = 0; i < totalTasksSize; i++) {
            try {
                // We want to make sure the policy didn't affect the completion queue of the ExecutorCompletionService
                results += completionService.take().get();
            } catch (InterruptedException e) {
                Assert.fail();
            } catch (ExecutionException e) {
                Assert.fail();
            }
        }
        Assert.assertEquals(taskCounter.get(), 0);
        Assert.assertEquals(results, totalTasksSize);
    }
}
