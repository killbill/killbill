/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.api.AbortAfterFirstFailureListener;
import org.killbill.billing.api.FlakyInvokedMethodListener;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.test.config.TestKillbillConfigSource;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.glue.RedissonCacheClientProvider;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.clock.DistributedClockMock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.redisson.api.RedissonClient;
import org.skife.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import redis.embedded.RedisServer;

import static org.testng.ITestResult.CREATED;
import static org.testng.ITestResult.FAILURE;
import static org.testng.ITestResult.SKIP;
import static org.testng.ITestResult.STARTED;
import static org.testng.ITestResult.SUCCESS;
import static org.testng.ITestResult.SUCCESS_PERCENTAGE_FAILURE;

@Listeners({FlakyInvokedMethodListener.class, AbortAfterFirstFailureListener.class})
public class GuicyKillbillTestSuite implements IHookable {

    // Use the simple name here to save screen real estate
    protected static final Logger log = LoggerFactory.getLogger(KillbillTestSuite.class.getSimpleName());

    // Variables set in @BeforeSuite
    protected static Map<String, String> extraPropertiesForTestSuite = new HashMap<String, String>();
    // The clock needs to be setup early, as it is needed when starting the server, but see below
    @VisibleForTesting
    protected static ClockMock theRealClock = new ClockMock();

    protected ClockMock clock = theRealClock;
    protected KillbillConfigSource configSource;
    protected ConfigSource skifeConfigSource;

    private RedissonClient redissonClient;

    @Inject
    protected InternalCallContextFactory internalCallContextFactory;

    @Inject
    protected MutableInternalCallContext internalCallContext;

    @Inject
    protected MutableCallContext callContext;

    private RedisServer redisServer;

    private boolean hasFailed = false;

    public static void refreshCallContext(final UUID accountId,
                                          final Clock clock,
                                          final InternalCallContextFactory internalCallContextFactory,
                                          final MutableCallContext callContext,
                                          final MutableInternalCallContext internalCallContext) {
        final InternalTenantContext tmp = internalCallContextFactory.createInternalTenantContext(accountId, callContext);
        internalCallContext.setAccountRecordId(tmp.getAccountRecordId());
        internalCallContext.setFixedOffsetTimeZone(tmp.getFixedOffsetTimeZone());
        internalCallContext.setReferenceTime(tmp.getReferenceLocalTime());
        internalCallContext.setCreatedDate(clock.getUTCNow());
        internalCallContext.setUpdatedDate(clock.getUTCNow());

        callContext.setDelegate(accountId, internalCallContext);
    }

    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        return getConfigSource(null, extraProperties);
    }

    protected KillbillConfigSource getConfigSource(final String file, final Map<String, String> extraProperties) {
        try {
            return new TestKillbillConfigSource(file, DBTestingHelper.class, extraProperties);
        } catch (final Exception e) {
            final AssertionError assertionError = new AssertionError("Initialization error");
            assertionError.initCause(e);
            throw assertionError;
        }
    }

    protected void refreshCallContext(final UUID accountId) {
        refreshCallContext(accountId, clock, internalCallContextFactory, callContext, internalCallContext);
    }

    // Refresh the createdDate
    protected void refreshCallContext() {
        refreshCallContext(callContext.getAccountId(), clock, internalCallContextFactory, callContext, internalCallContext);
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeMethodAlwaysRun(final Method method) throws Exception {
        if (hasFailed()) {
            return;
        }

        log.info("***************************************************************************************************");
        log.info("*** Starting test {}:{}", method.getDeclaringClass().getName(), method.getName());
        log.info("***************************************************************************************************");

        if (internalCallContext != null) {
            internalCallContext.reset();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethodAlwaysRun(final Method method, final ITestResult result) throws Exception {
        if (hasFailed()) {
            return;
        }

        final String tag;
        switch (result.getStatus()) {
            case SUCCESS:
                tag = "SUCCESS";
                break;
            case FAILURE:
                tag = "!!! FAILURE !!!";
                break;
            case SKIP:
                tag = "SKIP";
                break;
            case SUCCESS_PERCENTAGE_FAILURE:
                tag = "SUCCESS WITHIN PERCENTAGE";
                break;
            case STARTED:
                tag = "STARTED";
                break;
            case CREATED:
                tag = "CREATED";
                break;
            default:
                tag = "UNKNOWN";
                break;
        }

        log.info("***************************************************************************************************");
        log.info("***   Ending test {}:{} {} ({} s.)", new Object[]{method.getDeclaringClass().getName(), method.getName(),
                                                                    tag,
                                                                    (result.getEndMillis() - result.getStartMillis()) / 1000});
        log.info("***************************************************************************************************");
        if (!hasFailed && !result.isSuccess()) {
            // Ignore if the current test method is flaky
            final ITestNGMethod testNGMethod = result.getMethod();
            final boolean isFlakyTest = testNGMethod != null && testNGMethod.getRetryAnalyzer(result) != null && testNGMethod.getRetryAnalyzer(result) instanceof FlakyRetryAnalyzer;
            if (!isFlakyTest) {
                hasFailed = true;
            }
        }
    }

    // Note: assertions should not be run in before / after hooks, as the associated test result won't be correctly updated.
    // Use this wrapper instead.
    @Override
    public void run(final IHookCallBack callBack, final ITestResult testResult) {
        // Make sure we start with a clean state
        assertListenerStatus();

        // Run the actual test
        callBack.runTestMethod(testResult);

        if (testResult.getThrowable() == null) {
            // Make sure we finish in a clean state (if the test didn't fail)
            assertListenerStatus();
        }
    }

    protected void assertListenerStatus() {
        // No-op
    }

    @BeforeSuite(alwaysRun = true)
    public void globalBeforeSuite() {
        extraPropertiesForTestSuite.put("org.killbill.security.shiroResourcePath", "classpath:org/killbill/billing/util/shiro.ini");

        if (Boolean.valueOf(System.getProperty("killbill.test.redis", "false"))) {
            redisServer = new RedisServer(56379);
            redisServer.start();

            redissonClient = new RedissonCacheClientProvider("redis://127.0.0.1:56379", 1, null).get();

            theRealClock = new DistributedClockMock();
            ((DistributedClockMock) theRealClock).setRedissonClient(redissonClient);

            extraPropertiesForTestSuite.put("org.killbill.cache.config.redis", "true");
            extraPropertiesForTestSuite.put("org.killbill.cache.config.redis.url", "redis://127.0.0.1:56379");
        } else {
            theRealClock.resetDeltaFromReality();
        }

        // The clock needs to be setup early in @BeforeSuite, as it is needed when starting the server, but see below
        clock = theRealClock;
    }

    @BeforeClass(alwaysRun = true)
    public void globalBeforeTest() {
        configSource = getConfigSource(extraPropertiesForTestSuite);
        skifeConfigSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        };

        // We need to set the instance variable in each subsequent class instantiated in the suite
        clock = Mockito.mock(ClockMock.class,
                             new Answer() {
                                 @Override
                                 public Object answer(final InvocationOnMock invocation) throws Throwable {
                                     final Object answer = invocation.getMethod().invoke(theRealClock, invocation.getArguments());
                                     final DateTime utcNow = theRealClock.getUTCNow();

                                     if (callContext != null) {
                                         callContext.setCreatedDate(utcNow);
                                     }
                                     if (internalCallContext != null) {
                                         internalCallContext.setCreatedDate(utcNow);
                                         internalCallContext.setUpdatedDate(utcNow);
                                     }

                                     return answer;
                                 }
                             });
    }

    @AfterSuite(alwaysRun = true)
    public void globalAfterSuite() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    public boolean hasFailed() {
        return hasFailed || AbortAfterFirstFailureListener.hasFailures();
    }
}
