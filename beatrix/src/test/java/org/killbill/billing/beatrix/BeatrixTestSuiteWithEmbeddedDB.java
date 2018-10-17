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

package org.killbill.billing.beatrix;

import java.util.HashMap;
import java.util.Map;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.platform.api.KillbillConfigSource;

import com.google.common.collect.ImmutableMap;

public abstract class BeatrixTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Map<String, String> DEFAULT_BEATRIX_PROPERTIES = ImmutableMap.<String, String>builder().put("org.killbill.catalog.uri", "catalogs/default/catalogTest.xml")
                                                                                                                  .put("org.killbill.invoice.maxDailyNumberOfItemsSafetyBound", "30")
                                                                                                                  .put("org.killbill.payment.retry.days", "8,8,8,8,8,8,8,8")
                                                                                                                  .put("org.killbill.osgi.bundle.install.dir", "/var/tmp/beatrix-bundles")
                                                                                                                  // The default value is 50, i.e. wait 50 x 100ms = 5s to get the lock. This isn't always enough and can lead to random tests failures
                                                                                                                  // in the listener status: after moving the clock, if there are two notifications triggering an invoice run, we typically expect
                                                                                                                  // both an INVOICE and a NULL_INVOICE event. If the invoice generation takes too long, the NULL_INVOICE event is never generated
                                                                                                                  // (LockFailedException): the test itself doesn't fail (the correct invoice is generated), but assertListenerStatus() would.
                                                                                                                  .put("org.killbill.invoice.globalLock.retries", "300")
                                                                                                                  .build();

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        return getConfigSource(null, allExtraProperties);
    }
}
