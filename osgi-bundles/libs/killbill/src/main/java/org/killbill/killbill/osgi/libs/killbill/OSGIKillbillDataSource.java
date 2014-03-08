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

package org.killbill.killbill.osgi.libs.killbill;

import javax.sql.DataSource;

import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class OSGIKillbillDataSource extends OSGIKillbillLibraryBase {

    private static final String DATASOURCE_SERVICE_NAME = "javax.sql.DataSource";

    private final ServiceTracker<DataSource, DataSource> dataSourceTracker;


    public OSGIKillbillDataSource(BundleContext context) {
        dataSourceTracker = new ServiceTracker(context, DATASOURCE_SERVICE_NAME, null);
        dataSourceTracker.open();
    }

    public void close() {
        if (dataSourceTracker != null) {
            dataSourceTracker.close();
        }
    }

    public DataSource getDataSource() {
        return withServiceTracker(dataSourceTracker, new APICallback<DataSource, DataSource>(DATASOURCE_SERVICE_NAME) {
            @Override
            public DataSource executeWithService(final DataSource service) {
                return dataSourceTracker.getService();
            }
        });
    }
}
