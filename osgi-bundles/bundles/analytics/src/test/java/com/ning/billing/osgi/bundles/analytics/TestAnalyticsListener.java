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

import java.util.Properties;
import java.util.UUID;

import org.testng.annotations.Test;

import junit.framework.Assert;

import static com.ning.billing.osgi.bundles.analytics.AnalyticsListener.ANALYTICS_ACCOUNTS_BLACKLIST_PROPERTY;

public class TestAnalyticsListener extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testBlacklist() throws Exception {
        final Properties properties = new Properties();
        AnalyticsListener analyticsListener = new AnalyticsListener(logService, killbillAPI, killbillDataSource, null);

        // No account is blacklisted
        Assert.assertFalse(analyticsListener.isAccountBlacklisted(UUID.randomUUID()));

        final UUID blackListedAccountId = UUID.randomUUID();
        properties.put(ANALYTICS_ACCOUNTS_BLACKLIST_PROPERTY, String.format("%s,%s", UUID.randomUUID(), blackListedAccountId));
        analyticsListener = new AnalyticsListener(logService, killbillAPI, killbillDataSource, null, properties);

        // Other accounts are blacklisted
        Assert.assertFalse(analyticsListener.isAccountBlacklisted(UUID.randomUUID()));

        // Blacklist
        Assert.assertTrue(analyticsListener.isAccountBlacklisted(blackListedAccountId));
    }
}
