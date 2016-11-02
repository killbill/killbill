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

package org.killbill.billing.jaxrs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.killbill.billing.util.config.definition.JaxrsConfig;
import org.killbill.commons.concurrent.WithProfilingThreadPoolExecutor;

public class JaxrsExecutors {


    private static final long TIMEOUT_EXECUTOR_SEC = 3L;

    private static final String JAXRS_THREAD_PREFIX = "jaxrs-th-";
    private static final String JAXRS_TH_GROUP_NAME = "jaxrs-grp";


    private final JaxrsConfig JaxrsConfig;

    private volatile ExecutorService jaxrsExecutorService;

    @Inject
    public JaxrsExecutors(JaxrsConfig JaxrsConfig) {
        this.JaxrsConfig = JaxrsConfig;

    }

    public void initialize() {
        this.jaxrsExecutorService = createJaxrsExecutorService();
    }


    public void stop() throws InterruptedException {
        jaxrsExecutorService.shutdownNow();
        jaxrsExecutorService.awaitTermination(TIMEOUT_EXECUTOR_SEC, TimeUnit.SECONDS);
        jaxrsExecutorService = null;

    }

    public ExecutorService getJaxrsExecutorService() {
        return jaxrsExecutorService;
    }

    private ExecutorService createJaxrsExecutorService() {
        return new WithProfilingThreadPoolExecutor(JaxrsConfig.getJaxrsThreadNb(),
                                                   JaxrsConfig.getJaxrsThreadNb(),
                                                   0L,
                                                   TimeUnit.MILLISECONDS,
                                                   new LinkedBlockingQueue<Runnable>(),
                                                   new ThreadFactory() {

                                                       @Override
                                                       public Thread newThread(final Runnable r) {
                                                           final Thread th = new Thread(new ThreadGroup(JAXRS_TH_GROUP_NAME), r);
                                                           th.setName(JAXRS_THREAD_PREFIX + th.getId());
                                                           return th;
                                                       }
                                                   });

    }
}
