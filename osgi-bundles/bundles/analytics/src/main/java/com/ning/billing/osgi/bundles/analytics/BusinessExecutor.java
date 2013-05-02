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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.service.log.LogService;

import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessExecutor extends ThreadPoolExecutor {

    private static final Integer NB_THREADS = Integer.valueOf(System.getProperty("com.ning.billing.osgi.bundles.analytics.nb_threads", "100"));

    private final OSGIKillbillLogService logService;

    public static BusinessExecutor create(final OSGIKillbillLogService logService) {
        return new BusinessExecutor(0,
                                    NB_THREADS,
                                    60L,
                                    TimeUnit.SECONDS,
                                    new SynchronousQueue<Runnable>(),
                                    new NamedThreadFactory("osgi-analytics"),
                                    logService);
    }

    public BusinessExecutor(final int corePoolSize,
                            final int maximumPoolSize,
                            final long keepAliveTime,
                            final TimeUnit unit,
                            final BlockingQueue<Runnable> workQueue,
                            final ThreadFactory threadFactory,
                            final OSGIKillbillLogService logService) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.logService = logService;
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return super.submit(WrappedCallable.wrap(logService, task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        // HACK: assumes ThreadPoolExecutor will create a callable and call execute()
        // (can't wrap the runnable here or exception isn't re-thrown when Future.get() is called)
        return super.submit(task, result);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return super.submit(WrappedRunnable.wrap(logService, task));
    }

    @Override
    public void execute(final Runnable command) {
        super.execute(WrappedRunnable.wrap(logService, command));
    }

    private static class WrappedCallable<T> implements Callable<T> {

        private final OSGIKillbillLogService logService;
        private final Callable<T> callable;

        private WrappedCallable(final OSGIKillbillLogService logService, final Callable<T> callable) {
            this.logService = logService;
            this.callable = callable;
        }

        public static <T> Callable<T> wrap(final OSGIKillbillLogService logService, final Callable<T> callable) {
            return callable instanceof WrappedCallable ? callable : new WrappedCallable<T>(logService, callable);
        }

        @Override
        public T call() throws Exception {
            final Thread currentThread = Thread.currentThread();

            try {
                return callable.call();
            } catch (Exception e) {
                // since callables are expected to sometimes throw exceptions, log this at DEBUG instead of ERROR
                logService.log(LogService.LOG_DEBUG, currentThread + " ended with an exception", e);

                throw e;
            } catch (Error e) {
                logService.log(LogService.LOG_ERROR, currentThread + " ended with an exception", e);

                throw e;
            } finally {
                logService.log(LogService.LOG_DEBUG, currentThread + " finished executing");
            }
        }
    }

    private static class WrappedRunnable implements Runnable {

        private final OSGIKillbillLogService logService;
        private final Runnable runnable;

        private WrappedRunnable(final OSGIKillbillLogService logService, final Runnable runnable) {
            this.logService = logService;
            this.runnable = runnable;
        }

        public static Runnable wrap(final OSGIKillbillLogService logService, final Runnable runnable) {
            return runnable instanceof WrappedRunnable ? runnable : new WrappedRunnable(logService, runnable);
        }

        @Override
        public void run() {
            final Thread currentThread = Thread.currentThread();

            try {
                runnable.run();
            } catch (Throwable e) {
                logService.log(LogService.LOG_ERROR, currentThread + " ended abnormally with an exception", e);
            }

            logService.log(LogService.LOG_DEBUG, currentThread + " finished executing");
        }
    }

    /**
     * Factory that sets the name of each thread it creates to {@code [name]-[id]}.
     * This makes debugging stack traces much easier.
     */
    private static class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger count = new AtomicInteger(0);
        private final String name;

        public NamedThreadFactory(final String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable);

            thread.setName(name + "-" + count.incrementAndGet());

            return thread;
        }
    }
}
