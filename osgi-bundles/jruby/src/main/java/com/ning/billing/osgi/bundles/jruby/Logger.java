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

package com.ning.billing.osgi.bundles.jruby;

import javax.annotation.Nullable;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

public class Logger {

    // The name of the LogService
    private static final String LOG_SERVICE_NAME = "org.osgi.service.log.LogService";

    // The ServiceTracker to emit log services
    private ServiceTracker logTracker;

    public void start(final BundleContext context) {
        // Track the log service using a ServiceTracker
        logTracker = new ServiceTracker(context, LOG_SERVICE_NAME, null);
        logTracker.open();
    }

    public void close() {
        if (logTracker != null) {
            logTracker.close();
        }
    }

    public void log(final int level, final String message) {
        log(level, message, null);
    }

    public void log(final int level, final String message, @Nullable final Throwable t) {
        // log using the LogService if available
        final Object log = logTracker.getService();
        if (log != null) {
            if (t == null) {
                ((LogService) log).log(level, message);
            } else {
                ((LogService) log).log(level, message, t);
            }
        } else {
            if (level >= 2) {
                System.out.println(message);
            } else {
                System.err.println(message);
            }

            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }
}
