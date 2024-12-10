package org.killbill.billing.jaxrs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import org.killbill.billing.util.config.definition.JaxrsConfig;

public class JaxrsExecutors {

    private static final long TIMEOUT_EXECUTOR_SEC = 3L;

    private static final String JAXRS_THREAD_PREFIX = "jaxrs-th-";
    private static final String JAXRS_TH_GROUP_NAME = "jaxrs-grp";

    private final JaxrsConfig jaxrsConfig;

    private final AtomicReference<ExecutorService> jaxrsExecutorService = new AtomicReference<>();

    @Inject
    public JaxrsExecutors(JaxrsConfig jaxrsConfig) {
        this.jaxrsConfig = jaxrsConfig;
    }

    public void initialize() {
        if (jaxrsExecutorService.get() == null || jaxrsExecutorService.get().isShutdown()) {
            jaxrsExecutorService.compareAndSet(null, createJaxrsExecutorService());
        }
    }

    public void stop() throws InterruptedException {
        ExecutorService executorService = jaxrsExecutorService.getAndSet(null);
        if (executorService != null) {
            executorService.shutdownNow();
            executorService.awaitTermination(TIMEOUT_EXECUTOR_SEC, TimeUnit.SECONDS);
        }
    }

    public ExecutorService getJaxrsExecutorService() {
        return jaxrsExecutorService.get();
    }

    private ExecutorService createJaxrsExecutorService() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(new ThreadGroup(JAXRS_TH_GROUP_NAME), r);
                th.setName(JAXRS_THREAD_PREFIX + th.getId());
                return th;
            }
        });
    }
}
