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

package com.ning.killbill.osgi.libs.killbill;

import javax.annotation.Nullable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

public class OSGIKillbillLogService extends OSGIKillbillLibraryBase implements LogService {

    private static final String LOG_SERVICE_NAME = "org.osgi.service.log.LogService";

    private final ServiceTracker<LogService, LogService> logTracker;


    public OSGIKillbillLogService(BundleContext context) {
        super();
        logTracker = new ServiceTracker(context, LOG_SERVICE_NAME, null);
        logTracker.open();
    }

    public void close() {
        if (logTracker != null) {
            logTracker.close();
        }
    }

    @Override
    public void log(final int level, final String message) {
        logInternal(level, message, null);
    }

    @Override
    public void log(final int level, final String message, final Throwable exception) {
        logInternal(level, message, exception);
    }

    @Override
    public void log(final ServiceReference sr, final int level, final String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void log(final ServiceReference sr, final int level, final String message, final Throwable exception) {
        throw new UnsupportedOperationException();
    }

    private void logInternal(final int level, final String message, @Nullable final Throwable t) {

        withServiceTracker(logTracker, new APICallback<Void, LogService>(LOG_SERVICE_NAME) {
            @Override
            public Void executeWithService(final LogService service) {
                if (t == null) {
                    service.log(level, message);
                } else {
                    service.log(level, message, t);
                }
                return null;
            }

            protected Void executeWithNoService() {
                if (level >= 2) {
                    System.out.println(message);
                } else {
                    System.err.println(message);
                }
                if (t != null) {
                    t.printStackTrace(System.err);
                }
                return null;
            }
        });
    }
}
