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

package org.killbill.billing.payment.logging;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

public class TestLoggingHelper {

    public static SpyLogger withSpyLogger(final Class loggingClass, final Callable<Void> callable) throws Exception {
        final Logger regularLogger = LoggerFactory.getLogger(loggingClass);
        final SpyLogger spyLogger = new SpyLogger(loggingClass.getName());

        try {
            injectLoggerIntoLoggerFactory(loggingClass, spyLogger);
            callable.call();
        } finally {
            injectLoggerIntoLoggerFactory(loggingClass, regularLogger);
        }
        return spyLogger;
    }

    private static void injectLoggerIntoLoggerFactory(final Class loggingClass, final Logger logger) throws Exception {
        final SimpleLoggerFactory simpleLoggerFactory = (SimpleLoggerFactory) StaticLoggerBinder.getSingleton().getLoggerFactory();
        final Field loggerMapField = SimpleLoggerFactory.class.getDeclaredField("loggerMap");
        loggerMapField.setAccessible(true);
        final Map loggerMap = (Map) loggerMapField.get(simpleLoggerFactory);
        loggerMap.put(loggingClass.getName(), logger);
    }
}
