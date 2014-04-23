/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.killbill.osgi.libs.killbill;

import java.util.Properties;

import org.killbill.billing.osgi.api.OSGIConfigProperties;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class OSGIConfigPropertiesService extends OSGIKillbillLibraryBase implements OSGIConfigProperties {

    private final ServiceTracker killbillTracker;

    public OSGIConfigPropertiesService(final BundleContext context) {
        killbillTracker = new ServiceTracker(context, OSGIConfigProperties.class.getName(), null);
        killbillTracker.open();
    }

    public void close() {
        if (killbillTracker != null) {
            killbillTracker.close();
        }
    }

    @Override
    public String getString(final String propertyName) {
        return withServiceTracker(killbillTracker, new APICallback<String, OSGIConfigProperties>(OSGIConfigProperties.class.getName()) {
            @Override
            public String executeWithService(final OSGIConfigProperties service) {
                return service.getString(propertyName);
            }
        });
    }

    @Override
    public Properties getProperties() {
        return withServiceTracker(killbillTracker, new APICallback<Properties, OSGIConfigProperties>(OSGIConfigProperties.class.getName()) {
            @Override
            public Properties executeWithService(final OSGIConfigProperties service) {
                return service.getProperties();
            }
        });
    }
}
