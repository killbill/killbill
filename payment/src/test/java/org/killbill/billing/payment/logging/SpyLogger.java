/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import com.google.common.base.Optional;

public class SpyLogger implements Logger {

    private Logger wrappedLogger;

    private List<LogMessage> logMessageList = new ArrayList<LogMessage>();

    public SpyLogger(Logger wrappedLogger) {
        this.wrappedLogger = wrappedLogger;
    }

    public static final String LOG_LEVEL_TRACE = "TRACE";
    public static final String LOG_LEVEL_DEBUG = "DEBUG";
    public static final String LOG_LEVEL_INFO = "INFO";
    public static final String LOG_LEVEL_WARN = "WARN";
    public static final String LOG_LEVEL_ERROR = "ERROR";

    /**
     * Return the name of this <code>Logger</code> instance.
     * @return name of this logger instance
     */
    @Override
    public String getName() {
        return wrappedLogger.getName();
    }

    /**
     * Is the logger instance enabled for the TRACE level?
     *
     * @return True if this Logger is enabled for the TRACE level,
     *         false otherwise.
     * @since 1.4
     */
    @Override
    public boolean isTraceEnabled() {
        return wrappedLogger.isTraceEnabled();
    }

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message string to be logged
     * @since 1.4
     */
    @Override
    public void trace(final String msg) {
        wrappedLogger.trace(msg);
        log(LOG_LEVEL_TRACE, msg, null);
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and argument.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level. </p>
     *
     * @param format the format string
     * @param arg    the argument
     * @since 1.4
     */
    @Override
    public void trace(final String format, final Object arg) {
        wrappedLogger.trace(format, arg);
        formatAndSave(LOG_LEVEL_TRACE, format, arg, null);
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level. </p>
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @since 1.4
     */
    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        wrappedLogger.trace(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_TRACE, format, arg1, arg2);
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous string concatenation when the logger
     * is disabled for the TRACE level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for TRACE. The variants taking {@link #trace(String, Object) one} and
     * {@link #trace(String, Object, Object) two} arguments exist solely in order to avoid this hidden cost.</p>
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     * @since 1.4
     */
    @Override
    public void trace(final String format, final Object... arguments) {
        wrappedLogger.trace(format, arguments);
        formatAndSave(LOG_LEVEL_TRACE, format, arguments);

    }

    /**
     * Log an exception (throwable) at the TRACE level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     * @since 1.4
     */
    @Override
    public void trace(final String msg, final Throwable t) {
        wrappedLogger.trace(msg, t);
        log(LOG_LEVEL_TRACE, msg, t);
    }

    /**
     * Is the logger instance enabled for the DEBUG level?
     *
     * @return True if this Logger is enabled for the DEBUG level,
     *         false otherwise.
     */
    @Override
    public boolean isDebugEnabled() {
        return wrappedLogger.isDebugEnabled();
    }

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void debug(final String msg) {
        wrappedLogger.debug(msg);
        log(LOG_LEVEL_DEBUG, msg, null);
    }

    /**
     * Log a message at the DEBUG level according to the specified format
     * and argument.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level. </p>
     *  @param format the format string
     * @param arg    the argument
     */
    @Override
    public void debug(final String format, final Object arg) {
        wrappedLogger.debug(format, arg);
        formatAndSave(LOG_LEVEL_DEBUG, format, arg, null);
    }

    /**
     * Log a message at the DEBUG level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level. </p>
     *  @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        wrappedLogger.debug(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_DEBUG, format, arg1, arg2);
    }

    /**
     * Log a message at the DEBUG level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous string concatenation when the logger
     * is disabled for the DEBUG level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for DEBUG. The variants taking
     * {@link #debug(String, Object) one} and {@link #debug(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.</p>
     *  @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void debug(final String format, final Object... arguments) {
        wrappedLogger.debug(format, arguments);
        formatAndSave(LOG_LEVEL_DEBUG, format, arguments);
    }

    /**
     * Log an exception (throwable) at the DEBUG level with an
     * accompanying message.
     *  @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    @Override
    public void debug(final String msg, final Throwable t) {
        wrappedLogger.debug(msg, t);
        log(LOG_LEVEL_DEBUG, msg, t);
    }

    /**
     * Is the logger instance enabled for the INFO level?
     *
     * @return True if this Logger is enabled for the INFO level,
     *         false otherwise.
     */
    @Override
    public boolean isInfoEnabled() {
        return wrappedLogger.isInfoEnabled();
    }

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void info(final String msg) {
        wrappedLogger.info(msg);
        log(LOG_LEVEL_INFO, msg, null);
    }

    /**
     * Log a message at the INFO level according to the specified format
     * and argument.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the INFO level. </p>
     *  @param format the format string
     * @param arg    the argument
     */
    @Override
    public void info(final String format, final Object arg) {
        wrappedLogger.info(format, arg);
        formatAndSave(LOG_LEVEL_INFO, format, arg, null);
    }

    /**
     * Log a message at the INFO level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the INFO level. </p>
     *  @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        wrappedLogger.info(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_INFO, format, arg1, arg2);
    }

    /**
     * Log a message at the INFO level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous string concatenation when the logger
     * is disabled for the INFO level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for INFO. The variants taking
     * {@link #info(String, Object) one} and {@link #info(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.</p>
     *  @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void info(final String format, final Object... arguments) {
        wrappedLogger.info(format, arguments);
        formatAndSave(LOG_LEVEL_INFO, format, arguments);
    }

    /**
     * Log an exception (throwable) at the INFO level with an
     * accompanying message.
     *  @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    @Override
    public void info(final String msg, final Throwable t) {
        wrappedLogger.info(msg, t);
        log(LOG_LEVEL_INFO, msg, t);
    }

    /**
     * Is the logger instance enabled for the WARN level?
     *
     * @return True if this Logger is enabled for the WARN level,
     *         false otherwise.
     */
    @Override
    public boolean isWarnEnabled() {
        return wrappedLogger.isWarnEnabled();
    }

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void warn(final String msg) {
        wrappedLogger.warn(msg);
        log(LOG_LEVEL_WARN, msg, null);
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and argument.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the WARN level. </p>
     *  @param format the format string
     * @param arg    the argument
     */
    @Override
    public void warn(final String format, final Object arg) {
        wrappedLogger.warn(format, arg);
        formatAndSave(LOG_LEVEL_WARN, format, arg, null);
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous string concatenation when the logger
     * is disabled for the WARN level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for WARN. The variants taking
     * {@link #warn(String, Object) one} and {@link #warn(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.</p>
     *  @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void warn(final String format, final Object... arguments) {
        wrappedLogger.warn(format, arguments);
        formatAndSave(LOG_LEVEL_WARN, format, arguments);
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the WARN level. </p>
     *  @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        wrappedLogger.warn(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_WARN, format, arg1, arg2);
    }

    /**
     * Log an exception (throwable) at the WARN level with an
     * accompanying message.
     *  @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    @Override
    public void warn(final String msg, final Throwable t) {
        wrappedLogger.warn(msg, t);
        log(LOG_LEVEL_WARN, msg, t);
    }

    /**
     * Is the logger instance enabled for the ERROR level?
     *
     * @return True if this Logger is enabled for the ERROR level,
     *         false otherwise.
     */
    @Override
    public boolean isErrorEnabled() {
        return wrappedLogger.isErrorEnabled();
    }

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void error(final String msg) {
        wrappedLogger.error(msg);
        log(LOG_LEVEL_ERROR, msg, null);
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and argument.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level. </p>
     *  @param format the format string
     * @param arg    the argument
     */
    @Override
    public void error(final String format, final Object arg) {
        wrappedLogger.error(format, arg);
        formatAndSave(LOG_LEVEL_ERROR, format, arg, null);
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level. </p>
     *  @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        wrappedLogger.error(format, arg1, arg2);
        formatAndSave(LOG_LEVEL_ERROR, format, arg1, arg2);
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     * <p/>
     * <p>This form avoids superfluous string concatenation when the logger
     * is disabled for the ERROR level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for ERROR. The variants taking
     * {@link #error(String, Object) one} and {@link #error(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.</p>
     *  @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void error(final String format, final Object... arguments) {
        wrappedLogger.error(format, arguments);
        formatAndSave(LOG_LEVEL_ERROR, format, arguments);
    }

    /**
     * Log an exception (throwable) at the ERROR level with an
     * accompanying message.
     *  @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    @Override
    public void error(final String msg, final Throwable t) {
        wrappedLogger.error(msg, t);
        log(LOG_LEVEL_ERROR, msg, t);
    }

    /**
     * returns a list with the stored log messages
     *
     * @return a list with the stored log messages
     */
    public List<LogMessage> getLogMessageList() {
        return logMessageList;
    }

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
        log(logLevel, tp.getMessage(), tp.getThrowable());
    }

    private void formatAndSave(String logLevel, String format, Object... arguments) {
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        log(logLevel, tp.getMessage(), tp.getThrowable());
    }

    private void log(String logLevel, String message, Throwable t) {
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

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        wrappedLogger.error(marker, msg, t);
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        wrappedLogger.error(marker, format, arguments);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        wrappedLogger.error(marker, format, arg1, arg2);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        wrappedLogger.error(marker, format, arg);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        wrappedLogger.error(marker, msg);
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return wrappedLogger.isErrorEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        wrappedLogger.warn(marker, msg, t);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        wrappedLogger.warn(marker, format, arguments);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        wrappedLogger.warn(marker, format, arg1, arg2);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        wrappedLogger.warn(marker, format, arg);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        wrappedLogger.warn(marker, msg);
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return wrappedLogger.isWarnEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        wrappedLogger.info(marker, msg, t);
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        wrappedLogger.info(marker, format, arguments);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        wrappedLogger.info(marker, format, arg1, arg2);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        wrappedLogger.info(marker, format, arg);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        wrappedLogger.info(marker, msg);
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return wrappedLogger.isInfoEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        wrappedLogger.debug(marker, msg, t);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        wrappedLogger.debug(marker, format, arguments);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        wrappedLogger.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        wrappedLogger.debug(marker, format, arg);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        wrappedLogger.debug(marker, msg);
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return wrappedLogger.isDebugEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        wrappedLogger.trace(marker, msg, t);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        wrappedLogger.trace(marker, format, argArray);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        wrappedLogger.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        wrappedLogger.trace(marker, format, arg);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        wrappedLogger.trace(marker, msg);
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return wrappedLogger.isTraceEnabled(marker);
    }

}
