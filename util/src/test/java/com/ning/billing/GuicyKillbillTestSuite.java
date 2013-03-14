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

package com.ning.billing;

import java.lang.reflect.Method;
import java.util.Properties;

import javax.inject.Inject;

import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.ClockMock;

public class GuicyKillbillTestSuite {


    // Use the simple name here to save screen real estate
    protected static final Logger log = LoggerFactory.getLogger(KillbillTestSuite.class.getSimpleName());

    private boolean hasFailed = false;

    @Inject
    protected InternalCallContext internalCallContext;

    @Inject
    protected CallContext callContext;

    @Inject
    protected ClockMock clock;


    private static final ClockMock theStaticClock = new ClockMock();

    protected final ConfigSource configSource;

    public GuicyKillbillTestSuite() {
        final Properties properties = new Properties(System.getProperties());
        properties.put("user.timezone", "UTC");

        // Speed up the notification queue
        properties.put("killbill.billing.util.notificationq.sleep", "100");
        // Speed up the bus
        properties.put("killbill.billing.util.persistent.bus.sleep", "100");
        properties.put("killbill.billing.util.persistent.bus.nbThreads", "1");

        configSource = new SimplePropertyConfigSource(properties);

        // Ignore ehcache checks. Unfortunately, ehcache looks at system properties directly...
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    public static ClockMock getClock() {
        return theStaticClock;
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeMethodAlwaysRun(final Method method) throws Exception {
        log.info("***************************************************************************************************");
        log.info("*** Starting test {}:{}", method.getDeclaringClass().getName(), method.getName());
        log.info("***************************************************************************************************");
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
