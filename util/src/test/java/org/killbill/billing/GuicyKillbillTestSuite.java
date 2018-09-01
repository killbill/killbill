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
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.api.AbortAfterFirstFailureListener;
import org.killbill.billing.api.FlakyInvokedMethodListener;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.test.config.TestKillbillConfigSource;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.clock.DistributedClockMock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.FstCodec;
import org.redisson.config.Config;
import org.skife.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

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

    protected final KillbillConfigSource configSource;
    protected final ConfigSource skifeConfigSource;

    @Inject
    protected InternalCallContextFactory internalCallContextFactory;

    @Inject
    protected MutableInternalCallContext internalCallContext;

    @Inject
    protected MutableCallContext callContext;

    private RedisServer redisServer;

    protected final ClockMock clock;

    private boolean hasFailed = false;

    public GuicyKillbillTestSuite() {
        final ImmutableMap<String, String> extraProperties;
        final ClockMock theRealClock;
        if (Boolean.valueOf(System.getProperty("killbill.test.redis", "false"))) {
            extraProperties = ImmutableMap.<String, String>of("org.killbill.cache.config.redis", "true",
                                                              "org.killbill.cache.config.redis.url", "redis://127.0.0.1:56379",
                                                              "org.killbill.locker.config.redis", "true",
                                                              "org.killbill.locker.config.redis.url", "redis://127.0.0.1:56379");
            redisServer = new RedisServer(56379);
            redisServer.start();

            final Config redissonCfg = new Config();
            redissonCfg.setCodec(new FstCodec())
                       .useSingleServer()
                       .setAddress("redis://127.0.0.1:56379")
                       .setConnectionMinimumIdleSize(1);
            final RedissonClient redissonClient = Redisson.create(redissonCfg);

            theRealClock = new DistributedClockMock();
            ((DistributedClockMock) theRealClock).setRedissonClient(redissonClient);
        } else {
            extraProperties = ImmutableMap.<String, String>of();
            theRealClock = new ClockMock();
        }

        clock = Mockito.spy(theRealClock);
        final Answer answer = new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                // Sync clock and theRealClock
                final Object realAnswer = invocation.callRealMethod();
                invocation.getMethod().invoke(theRealClock, invocation.getArguments());

                // Update the contexts createdDate each time we move the clock
                final DateTime utcNow = theRealClock.getUTCNow();
                if (callContext != null) {
                    callContext.setCreatedDate(utcNow);
                }
                if (internalCallContext != null) {
                    internalCallContext.setCreatedDate(utcNow);
                    internalCallContext.setUpdatedDate(utcNow);
                }

                return realAnswer;
            }
        };
        Mockito.doAnswer(answer).when(clock).getUTCNow();
        Mockito.doAnswer(answer).when(clock).getNow(Mockito.any(DateTimeZone.class));
        Mockito.doAnswer(answer).when(clock).getUTCToday();
        Mockito.doAnswer(answer).when(clock).getToday(Mockito.any(DateTimeZone.class));
        Mockito.doAnswer(answer).when(clock).addDays(Mockito.anyInt());
        Mockito.doAnswer(answer).when(clock).addWeeks(Mockito.anyInt());
        Mockito.doAnswer(answer).when(clock).addMonths(Mockito.anyInt());
        Mockito.doAnswer(answer).when(clock).addYears(Mockito.anyInt());
        Mockito.doAnswer(answer).when(clock).addDeltaFromReality(Mockito.anyLong());
        Mockito.doAnswer(answer).when(clock).setTime(Mockito.any(DateTime.class));
        Mockito.doAnswer(answer).when(clock).resetDeltaFromReality();

        this.configSource = getConfigSource(extraProperties);
        this.skifeConfigSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        };
    }

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
            final boolean isFlakyTest = testNGMethod != null && testNGMethod.getRetryAnalyzer() != null && testNGMethod.getRetryAnalyzer() instanceof FlakyRetryAnalyzer;
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

    @AfterSuite(alwaysRun = true)
    public void globalAfterSuite() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    public boolean hasFailed() {
        return hasFailed || AbortAfterFirstFailureListener.hasFailures();
    }
}
