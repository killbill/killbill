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

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class FlakyRetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRIES = 3;

    private int count = 0;

    @Override
    public boolean retry(final ITestResult iTestResult) {
        if (iTestResult.isSuccess()) {
            return false;
        }

        if (shouldRetry()) {
            count++;
            return true;
        } else {
            return false;
        }
    }

    public boolean shouldRetry() {
        return count < MAX_RETRIES;
    }
}
