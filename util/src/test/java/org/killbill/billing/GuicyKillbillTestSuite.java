/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.test.config.TestKillbillConfigSource;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.skife.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableMap;

public class GuicyKillbillTestSuite {

    // Use the simple name here to save screen real estate
    protected static final Logger log = LoggerFactory.getLogger(KillbillTestSuite.class.getSimpleName());

    private boolean hasFailed = false;

    @Inject
    protected InternalCallContextFactory internalCallContextFactory;

    @Inject
    protected MutableInternalCallContext internalCallContext;

    @Inject
    protected CallContext callContext;

    @Inject
    protected ClockMock clock;

    private static final ClockMock theStaticClock = new ClockMock();

    protected final KillbillConfigSource configSource;
    protected final ConfigSource skifeConfigSource;

    public GuicyKillbillTestSuite() {
        this.configSource = getConfigSource();
        this.skifeConfigSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        };
    }

    protected KillbillConfigSource getConfigSource() {
        try {
            return new TestKillbillConfigSource(DBTestingHelper.class);
        } catch (final Exception e) {
            final AssertionError assertionError = new AssertionError("Initialization error");
            assertionError.initCause(e);
            throw assertionError;
        }
    }

    protected KillbillConfigSource getConfigSource(final String file) {
        return getConfigSource(file, ImmutableMap.<String, String>of());
    }

    protected KillbillConfigSource getConfigSource(final String file, final ImmutableMap<String, String> extraProperties) {
        try {
            return new TestKillbillConfigSource(file, DBTestingHelper.class, extraProperties);
        } catch (final Exception e) {
            final AssertionError assertionError = new AssertionError("Initialization error");
            assertionError.initCause(e);
            throw assertionError;
        }
    }

    public static ClockMock getClock() {
        return theStaticClock;
    }

    public static void refreshCallContext(final UUID accountId,
                                          final Clock clock,
                                          final InternalCallContextFactory internalCallContextFactory,
                                          final TenantContext callContext,
                                          final MutableInternalCallContext internalCallContext) {
        final InternalTenantContext tmp = internalCallContextFactory.createInternalTenantContext(accountId, callContext);
        internalCallContext.setAccountRecordId(tmp.getAccountRecordId());
        internalCallContext.setFixedOffsetTimeZone(tmp.getFixedOffsetTimeZone());
        internalCallContext.setReferenceTime(tmp.getReferenceTime());
        internalCallContext.setCreatedDate(clock.getUTCNow());
        internalCallContext.setUpdatedDate(clock.getUTCNow());
    }

    protected void refreshCallContext(final UUID accountId) {
        refreshCallContext(accountId, clock, internalCallContextFactory, callContext, internalCallContext);
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeMethodAlwaysRun(final Method method) throws Exception {
        log.info("***************************************************************************************************");
        log.info("*** Starting test {}:{}", method.getDeclaringClass().getName(), method.getName());
        log.info("***************************************************************************************************");

        if (internalCallContext != null) {
            internalCallContext.reset();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethodAlwaysRun(final Method method, final ITestResult result) throws Exception {
        log.info("***************************************************************************************************");
        log.info("***   Ending test {}:{} {} ({} s.)", new Object[]{method.getDeclaringClass().getName(), method.getName(),
                                                                    result.isSuccess() ? "SUCCESS" : "!!! FAILURE !!!",
                                                                    (result.getEndMillis() - result.getStartMillis()) / 1000});
        log.info("***************************************************************************************************");
        if (!hasFailed && !result.isSuccess()) {
            hasFailed = true;
        }
    }

    public boolean hasFailed() {
        return hasFailed;
    }
}
