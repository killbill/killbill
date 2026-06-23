/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.server.log;

import java.util.Objects;

import org.killbill.commons.utils.annotation.VisibleForTesting;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

public class ThreadNameBasedDiscriminator implements Discriminator<ILoggingEvent> {

    private static final String KEY = "threadName";
    private static final String ORG_KILLBILL_DOT = "org.killbill.";
    private static final String BILLING_DOT = "billing.";
    private static final String COMMONS_DOT = "commons.";

    private static final String ORG_KILLBILL_BILLING_DOT = ORG_KILLBILL_DOT + BILLING_DOT;
    private static final String ORG_KILLBILL_COMMONS_DOT = ORG_KILLBILL_DOT + COMMONS_DOT;
    private static final String COM_KILLBILL_BILLING_PLUGIN_DOT = "com.killbill.billing.plugin.";

    // Skip JDBI wrappers, profiling utilities, etc.
    private static final String[] SKIP_PREFIXES = {
            "org.killbill.billing.util.entity.",
            "org.killbill.billing.util.dao.",
            "org.killbill.commons.profiling.",
            "org.killbill.commons.jdbi."
    };

    // RETAIN_CLASS_REFERENCE is required to call getDeclaringClass() on stack frames.
    // The walker is stateless and safe to share across threads.
    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private boolean started;

    @Override
    public String getDiscriminatingValue(final ILoggingEvent iLoggingEvent) {
        final String element = getKillbillCaller();
        if (element != null) {
            return element;
        } else {
            return Thread.currentThread().getName();
        }
    }

    @Override
    public String getKey() {
        return KEY;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    @VisibleForTesting
    static int lookupNextToken(final char[] classNameChars, final int start, final String nextToken) {
        final char[] nextTokenChars = nextToken.toCharArray();
        int i;
        for (i = 0; i < nextTokenChars.length; i++) {
            if (start + i >= classNameChars.length) {
                return -1;
            }
            if (classNameChars[start + i] != nextTokenChars[i]) {
                return -1;
            }
        }
        return i + start;
    }

    @VisibleForTesting
    static String findNextToken(final char[] classNameChars, final int start, final char nextChar) {
        for (int i = start; i < classNameChars.length; i++) {
            if (classNameChars[i] == nextChar) {
                return new String(classNameChars, start, i - start + 1);
            }
        }
        return null;
    }

    @VisibleForTesting
    static String buildMarkerName(final String prefix, final char[] classNameChars, final int startPosition) {
        final StringBuilder markerName = new StringBuilder(prefix);
        for (int j = startPosition; j < classNameChars.length; j++) {
            final char kar = classNameChars[j];
            if (kar == '.') {
                break;
            }
            markerName.append(kar);
        }
        return markerName.toString();
    }

    @VisibleForTesting
    static String buildPluginMarkerName(final char[] classNameChars, final int startPosition) {
        final StringBuilder markerName = new StringBuilder();
        int next = startPosition;
        String cur;
        while ((cur = findNextToken(classNameChars, next, '.')) != null) {
            markerName.setLength(0);
            markerName.append(classNameChars, next, cur.length() - 1);
            next += cur.length();
        }
        return markerName.isEmpty() ? null : markerName.toString();
    }

    private String getKillbillCaller() {
        // Walk the call stack and return the first frame whose declaring class
        // produces a non-null marker. We skip our own frames so the discriminator
        // implementation itself never matches the org.killbill.billing.* rule.
        return STACK_WALKER.walk(frames -> frames
                .filter(frame -> frame.getDeclaringClass() != ThreadNameBasedDiscriminator.class)
                .map(frame -> extractMarker(frame.getDeclaringClass().getName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null));
    }

    /**
     * Compute the marker name for a given fully-qualified class name, or
     * {@code null} when the caller should be skipped (JDBI/entity/dao/profiling
     * wrappers) or does not belong to a Kill Bill / plugin package.
     *
     * <p>Isolated from the stack walking logic so that the parsing rules can be
     * unit-tested without spinning up a real call stack.</p>
     */
    @VisibleForTesting
    static String extractMarker(final String className) {
        if (className == null) {
            return null;
        }

        if (className.startsWith(ORG_KILLBILL_DOT)) {
            // Skip well-known infrastructure / wrapper packages
            for (final String skip : SKIP_PREFIXES) {
                if (className.startsWith(skip)) {
                    return null;
                }
            }

            final char[] classNameChars = className.toCharArray();

            // Extract the killbill module for Kill Bill proper calls...
            if (className.startsWith(ORG_KILLBILL_BILLING_DOT) &&
                classNameChars.length > ORG_KILLBILL_BILLING_DOT.length()) {
                return buildMarkerName(ORG_KILLBILL_BILLING_DOT, classNameChars, ORG_KILLBILL_BILLING_DOT.length());
            }

            // ...or for Kill Bill commons calls...
            if (className.startsWith(ORG_KILLBILL_COMMONS_DOT) &&
                classNameChars.length > ORG_KILLBILL_COMMONS_DOT.length()) {
                return buildMarkerName(ORG_KILLBILL_COMMONS_DOT, classNameChars, ORG_KILLBILL_COMMONS_DOT.length());
            }

            // ...otherwise fall back to the top-level package under org.killbill.
            if (classNameChars.length > ORG_KILLBILL_DOT.length()) {
                return buildMarkerName(ORG_KILLBILL_DOT, classNameChars, ORG_KILLBILL_DOT.length());
            }
            return null;
        }

        // Support for plugins published under com.killbill.billing.plugin.*
        if (className.startsWith(COM_KILLBILL_BILLING_PLUGIN_DOT)) {
            return buildPluginMarkerName(className.toCharArray(), COM_KILLBILL_BILLING_PLUGIN_DOT.length());
        }

        return null;
    }
}
