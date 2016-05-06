/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.SubstituteLogger;

import com.google.common.base.Optional;

public class SpyLogger extends SubstituteLogger {

    private final List<LogMessage> logMessageList = new ArrayList<LogMessage>();

    public SpyLogger(String loggerName) {
        super(loggerName, new LinkedBlockingQueue<SubstituteLoggingEvent>(), false);
    }

    public static final String LOG_LEVEL_TRACE = "TRACE";
    public static final String LOG_LEVEL_DEBUG = "DEBUG";
    public static final String LOG_LEVEL_INFO = "INFO";
    public static final String LOG_LEVEL_WARN = "WARN";
    public static final String LOG_LEVEL_ERROR = "ERROR";

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return super.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        super.trace(msg);
        save(LOG_LEVEL_TRACE, msg, null);
    }

    @Override
    public void trace(final String format, final Object arg) {
        super.trace(format, arg);
        formatAndSave(LOG_LEVEL_TRACE, format, arg, null);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        super.trace(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_TRACE, format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        super.trace(format, arguments);
        formatAndSave(LOG_LEVEL_TRACE, format, arguments);

    }

    @Override
    public void trace(final String msg, final Throwable t) {
        super.trace(msg, t);
        save(LOG_LEVEL_TRACE, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return super.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        super.debug(msg);
        save(LOG_LEVEL_DEBUG, msg, null);
    }

    @Override
    public void debug(final String format, final Object arg) {
        super.debug(format, arg);
        formatAndSave(LOG_LEVEL_DEBUG, format, arg, null);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        super.debug(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        super.debug(format, arguments);
        formatAndSave(LOG_LEVEL_DEBUG, format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        super.debug(msg, t);
        save(LOG_LEVEL_DEBUG, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return super.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        super.info(msg);
        save(LOG_LEVEL_INFO, msg, null);
    }

    @Override
    public void info(final String format, final Object arg) {
        super.info(format, arg);
        formatAndSave(LOG_LEVEL_INFO, format, arg, null);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        super.info(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_INFO, format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        super.info(format, arguments);
        formatAndSave(LOG_LEVEL_INFO, format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
        super.info(msg, t);
        save(LOG_LEVEL_INFO, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return super.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        super.warn(msg);
        save(LOG_LEVEL_WARN, msg, null);
    }

    @Override
    public void warn(final String format, final Object arg) {
        super.warn(format, arg);
        formatAndSave(LOG_LEVEL_WARN, format, arg, null);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        super.warn(format, arguments);
        formatAndSave(LOG_LEVEL_WARN, format, arguments);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        super.warn(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_WARN, format, arg1, arg2);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        super.warn(msg, t);
        save(LOG_LEVEL_WARN, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return super.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        super.error(msg);
        save(LOG_LEVEL_ERROR, msg, null);
    }

    @Override
    public void error(final String format, final Object arg) {
        super.error(format, arg);
        formatAndSave(LOG_LEVEL_ERROR, format, arg, null);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        super.error(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_ERROR, format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        super.error(format, arguments);
        formatAndSave(LOG_LEVEL_ERROR, format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
        super.error(msg, t);
        save(LOG_LEVEL_ERROR, msg, t);
    }

    /**
     * Returns a list with the stored log messages
     *
     * @return a list with the stored log messages
     */
    public List<LogMessage> getLogMessageList() {
        return logMessageList;
    }

    /**
     * Checks if a certain message has been logged. It has to fulfil the
     * given regex pattern. If a logLevel is provided, the expected message
     * also has to have this log level. If no log level has been provided,
     * a message is just compared to the regex pattern.
     *
     * @param regex pattern that the message should follow.
     * @param logLevel log level that the message should have.
     * @return true if a message has been found, false if no has been found
     */
    public boolean contains(String regex, Optional<String> logLevel) {
        Pattern pattern = Pattern.compile(regex);

        for (LogMessage logMessage : logMessageList) {
            final boolean messageMatches = pattern.matcher(logMessage.message).find();
            final boolean logLevelMatches = logLevel.isPresent() ? logLevel.get().equals(logMessage.logLevel) : true;

            if (messageMatches && logLevelMatches) {
                return true;
            }
        }
        return false;
    }

    private void formatAndSave(String logLevel, String format, Object arg1, Object arg2) {
        FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        save(logLevel, tp.getMessage(), tp.getThrowable());
    }

    private void formatAndSave(String logLevel, String format, Object... arguments) {
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        save(logLevel, tp.getMessage(), tp.getThrowable());
    }

    private void save(String logLevel, String message, Throwable t) {
        logMessageList.add(new LogMessage(logLevel, message, t));
    }

    public class LogMessage {

        public final String logLevel;

        public final String message;

        public final Throwable throwable;

        public LogMessage(final String logLevel, final String message, final Throwable throwable) {
            this.logLevel = logLevel;
            this.message = message;
            this.throwable = throwable;
        }
    }
}
