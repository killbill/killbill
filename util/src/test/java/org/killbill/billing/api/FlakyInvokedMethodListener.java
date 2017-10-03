/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.Reporter;

public class FlakyInvokedMethodListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult) {
    }

    @Override
    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult) {
        if (testResult.getStatus() != ITestResult.FAILURE) {
            return;
        }

        final IRetryAnalyzer retryAnalyzer = testResult.getMethod().getRetryAnalyzer();
        if (retryAnalyzer != null &&
            retryAnalyzer instanceof FlakyRetryAnalyzer &&
            !((FlakyRetryAnalyzer) retryAnalyzer).shouldRetry()) {
            // Don't fail the build (flaky test), mark it as SKIPPED
            testResult.setStatus(ITestResult.SKIP);
            Reporter.setCurrentTestResult(testResult);
        }
    }
}
