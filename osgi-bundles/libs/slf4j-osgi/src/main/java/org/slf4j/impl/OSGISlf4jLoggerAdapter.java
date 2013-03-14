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

package org.slf4j.impl;

import org.osgi.service.log.LogService;
import org.slf4j.spi.LocationAwareLogger;

import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public final class OSGISlf4jLoggerAdapter extends SimpleLogger {

    private OSGIKillbillLogService delegate;

    public OSGISlf4jLoggerAdapter(final OSGIKillbillLogService logger) {
        super(OSGISlf4jLoggerAdapter.class.getName());
        this.delegate = logger;
    }

    public void setDelegate(final OSGIKillbillLogService delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void log(final int level, final String message, final Throwable t) {
        if (delegate == null) {
            super.log(level, message, t);
        } else {
            final int logServiceLevel = convertLocationAwareLoggerToLogServiceLevel(level);
            if (t == null) {
                delegate.log(logServiceLevel, message);
            } else {
                delegate.log(logServiceLevel, message, t);
            }
        }
    }

    private int convertLocationAwareLoggerToLogServiceLevel(final int level) {
        if (level == LocationAwareLogger.TRACE_INT) {
            return LogService.LOG_DEBUG;
        } else if (level == LocationAwareLogger.DEBUG_INT) {
            return LogService.LOG_DEBUG;
        } else if (level == LocationAwareLogger.INFO_INT) {
            return LogService.LOG_INFO;
        } else if (level == LocationAwareLogger.WARN_INT) {
            return LogService.LOG_WARNING;
        } else if (level == LocationAwareLogger.ERROR_INT) {
            return LogService.LOG_ERROR;
        } else {
            // Be safe to avoid loosing messages
            return LogService.LOG_INFO;
        }
    }
}
