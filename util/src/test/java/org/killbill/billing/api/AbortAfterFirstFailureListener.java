/*
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

package org.killbill.billing.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;

public class AbortAfterFirstFailureListener implements IInvokedMethodListener {

    private static final Logger logger = LoggerFactory.getLogger(AbortAfterFirstFailureListener.class);

    private static boolean hasFailures = false;

    @Override
    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }

        synchronized (this) {
            if (hasFailures) {
                throw new SkipException("Skipping this test");
            }
        }
    }

    @Override
    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }

        if (testResult.getStatus() == ITestResult.FAILURE) {
            // Don't skip other tests with the current test method is flaky
            final boolean isFlakyTest = method.getTestMethod().getRetryAnalyzer() != null && method.getTestMethod().getRetryAnalyzer() instanceof FlakyRetryAnalyzer;
            if (!isFlakyTest) {
                synchronized (this) {
                    logger.warn("!!! Test failure, all other tests will be skipped: {} !!!", testResult);
                    hasFailures = true;
                }
            }
        }
    }

    public static boolean hasFailures() {
        return hasFailures;
    }
}
