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

package com.ning.billing.osgi.bundles.logger;

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Inspired by osgi-over-slf4j
public class KillbillLogWriter implements LogListener {

    private static final String UNKNOWN = "[Unknown]";

    private final Map<String, Logger> delegates = new HashMap<String, Logger>();

    // Invoked by the log service implementation for each log entry
    public void logged(final LogEntry entry) {
        final Bundle bundle = entry.getBundle();
        final Logger delegate = getDelegateForBundle(bundle);

        final ServiceReference serviceReference = entry.getServiceReference();
        final int level = entry.getLevel();
        final String message = entry.getMessage();
        final Throwable exception = entry.getException();

        if (serviceReference != null && exception != null) {
            log(delegate, serviceReference, level, message, exception);
        } else if (serviceReference != null) {
            log(delegate, serviceReference, level, message);
        } else if (exception != null) {
            log(delegate, level, message, exception);
        } else {
            log(delegate, level, message);
        }
    }

    private Logger getDelegateForBundle(/* @Nullable */ final Bundle bundle) {
        final String loggerName;
        if (bundle != null) {
            final String name = bundle.getSymbolicName();
            Version version = bundle.getVersion();
            if (version == null) {
                version = Version.emptyVersion;
            }
            loggerName = name + '.' + version;
        } else {
            loggerName = KillbillLogWriter.class.getName();
        }

        if (delegates.get(loggerName) == null) {
            synchronized (delegates) {
                if (delegates.get(loggerName) == null) {
                    delegates.put(loggerName, LoggerFactory.getLogger(loggerName));
                }
            }
        }

        return delegates.get(loggerName);
    }

    private void log(final Logger delegate, final int level, final String message) {
        switch (level) {
            case LogService.LOG_DEBUG:
                delegate.debug(message);
                break;
            case LogService.LOG_ERROR:
                delegate.error(message);
                break;
            case LogService.LOG_INFO:
                delegate.info(message);
                break;
            case LogService.LOG_WARNING:
                delegate.warn(message);
                break;
            default:
                break;
        }
    }

    private void log(final Logger delegate, final int level, final String message, final Throwable exception) {
        switch (level) {
            case LogService.LOG_DEBUG:
                delegate.debug(message, exception);
                break;
            case LogService.LOG_ERROR:
                delegate.error(message, exception);
                break;
            case LogService.LOG_INFO:
                delegate.info(message, exception);
                break;
            case LogService.LOG_WARNING:
                delegate.warn(message, exception);
                break;
            default:
                break;
        }
    }

    private void log(final Logger delegate, final ServiceReference sr, final int level, final String message) {
        switch (level) {
            case LogService.LOG_DEBUG:
                if (delegate.isDebugEnabled()) {
                    delegate.debug(createMessage(sr, message));
                }
                break;
            case LogService.LOG_ERROR:
                if (delegate.isErrorEnabled()) {
                    delegate.error(createMessage(sr, message));
                }
                break;
            case LogService.LOG_INFO:
                if (delegate.isInfoEnabled()) {
                    delegate.info(createMessage(sr, message));
                }
                break;
            case LogService.LOG_WARNING:
                if (delegate.isWarnEnabled()) {
                    delegate.warn(createMessage(sr, message));
                }
                break;
            default:
                break;
        }
    }

    private void log(final Logger delegate, final ServiceReference sr, final int level, final String message, final Throwable exception) {
        switch (level) {
            case LogService.LOG_DEBUG:
                if (delegate.isDebugEnabled()) {
                    delegate.debug(createMessage(sr, message), exception);
                }
                break;
            case LogService.LOG_ERROR:
                if (delegate.isErrorEnabled()) {
                    delegate.error(createMessage(sr, message), exception);
                }
                break;
            case LogService.LOG_INFO:
                if (delegate.isInfoEnabled()) {
                    delegate.info(createMessage(sr, message), exception);
                }
                break;
            case LogService.LOG_WARNING:
                if (delegate.isWarnEnabled()) {
                    delegate.warn(createMessage(sr, message), exception);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Formats the log message to indicate the service sending it, if known.
     *
     * @param sr      the ServiceReference sending the message.
     * @param message The message to log.
     * @return The formatted log message.
     */
    private String createMessage(final ServiceReference sr, final String message) {
        final StringBuilder output = new StringBuilder();
        if (sr != null) {
            output.append('[').append(sr.toString()).append(']');
        } else {
            output.append(UNKNOWN);
        }
        output.append(message);

        return output.toString();
    }
}
