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

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.skife.config.ConfigSource;

public class KillbillConfigSource implements ConfigSource {

    private final Properties properties;

    public KillbillConfigSource() {
        properties = new Properties(System.getProperties());
        properties.put("user.timezone", "UTC");

        // Speed up the notification queue
        properties.put("killbill.billing.util.notificationq.sleep", "100");
        // Speed up the bus
        properties.put("killbill.billing.util.persistent.bus.sleep", "100");
        properties.put("killbill.billing.util.persistent.bus.nbThreads", "1");
    }

    public String getString(final String propertyName) {
        return properties.getProperty(propertyName);
    }

    public void merge(final URL url) {
        final Properties properties = new Properties();
        try {
            properties.load(url.openStream());
            for (final String propertyName : properties.stringPropertyNames()) {
                setProperty(propertyName, properties.getProperty(propertyName));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setProperty(final String propertyName, final Object propertyValue) {
        properties.put(propertyName, propertyValue);
    }
}
