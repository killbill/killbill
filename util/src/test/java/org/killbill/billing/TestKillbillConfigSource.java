/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

import org.killbill.billing.util.KillbillConfigSource;

import com.google.common.collect.ImmutableMap;

public class TestKillbillConfigSource extends KillbillConfigSource {

    private final Map<String, String> extraDefaults;

    public TestKillbillConfigSource() {
        super();
        this.extraDefaults = ImmutableMap.<String, String>of();
    }

    public TestKillbillConfigSource(final String file) throws URISyntaxException, IOException {
        this(file, ImmutableMap.<String, String>of());
    }

    public TestKillbillConfigSource(final String file, final Map<String, String> extraDefaults) throws URISyntaxException, IOException {
        super(file);
        this.extraDefaults = extraDefaults;
        // extraDefaults changed, need to reload defaults
        populateDefaultProperties();
    }

    @Override
    protected Properties getDefaultProperties() {
        final Properties properties = super.getDefaultProperties();

        // Setup up DAO properties (this will be a no-op for fast tests)
        properties.put("org.killbill.dao.url", DBTestingHelper.get().getJdbcConnectionString());
        properties.put("org.killbill.dao.user", DBTestingHelper.get().getUsername());
        properties.put("org.killbill.dao.password", DBTestingHelper.get().getPassword());
        properties.put("org.killbill.billing.osgi.jdbc.url", DBTestingHelper.get().getJdbcConnectionString());
        properties.put("org.killbill.billing.osgi.jdbc.user", DBTestingHelper.get().getUsername());
        properties.put("org.killbill.billing.osgi.jdbc.password", DBTestingHelper.get().getPassword());

        // Speed up the notification queue
        properties.put("org.killbill.notificationq.main.sleep", "100");
        properties.put("org.killbill.notificationq.main.nbThreads", "1");
        properties.put("org.killbill.notificationq.main.prefetch", "1");
        properties.put("org.killbill.notificationq.main.claimed", "1");
        properties.put("org.killbill.notificationq.main.useInFlightQ", "false");
        // Speed up the buses
        properties.put("org.killbill.persistent.bus.main.sleep", "100");
        properties.put("org.killbill.persistent.bus.main.nbThreads", "1");
        properties.put("org.killbill.persistent.bus.main.prefetch", "1");
        properties.put("org.killbill.persistent.bus.main.claimed", "1");
        properties.put("org.killbill.persistent.bus.main.useInFlightQ", "false");
        properties.put("org.killbill.persistent.bus.external.sleep", "100");
        properties.put("org.killbill.persistent.bus.external.nbThreads", "1");
        properties.put("org.killbill.persistent.bus.external.prefetch", "1");
        properties.put("org.killbill.persistent.bus.external.claimed", "1");
        properties.put("org.killbill.persistent.bus.external.useInFlightQ", "false");

        if (extraDefaults != null) {
            for (final String key : extraDefaults.keySet()) {
                properties.put(key, extraDefaults.get(key));
            }
        }

        return properties;
    }

    @Override
    protected Properties getDefaultSystemProperties() {
        final Properties properties = super.getDefaultSystemProperties();
        properties.put("net.sf.ehcache.skipUpdateCheck", "true");
        properties.put("org.slf4j.simpleLogger.showDateTime", "true");
        return properties;
    }
}
