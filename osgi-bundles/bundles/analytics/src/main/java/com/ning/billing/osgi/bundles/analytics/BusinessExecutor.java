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

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import com.ning.billing.commons.concurrent.Executors;

import com.google.common.annotations.VisibleForTesting;

public class BusinessExecutor {

    @VisibleForTesting
    static final Integer NB_THREADS = Integer.valueOf(System.getProperty("com.ning.billing.osgi.bundles.analytics.nb_threads", "100"));

    public static Executor newCachedThreadPool() {
        // Note: we don't use the default rejection handler here (AbortPolicy) as we always want the tasks to be executed
        return Executors.newCachedThreadPool(0,
                                             NB_THREADS,
                                             "osgi-analytics-refresh",
                                             60L,
                                             TimeUnit.SECONDS,
                                             new CallerRunsPolicy());

    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(final String name) {
        return Executors.newSingleThreadScheduledExecutor(name,
                                                          new CallerRunsPolicy());
    }
}
