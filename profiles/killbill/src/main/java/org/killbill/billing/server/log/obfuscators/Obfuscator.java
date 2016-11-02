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

package org.killbill.billing.server.log.obfuscators;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.annotations.VisibleForTesting;

public abstract class Obfuscator {

    @VisibleForTesting
    static final String LOGGING_FILTER_NAME = "com.sun.jersey.api.container.filter.LoggingFilter";

    protected static final int DEFAULT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

    protected static final char PAD_CHAR = '*';

    public abstract String obfuscate(final String originalString, final ILoggingEvent event);

    protected String obfuscate(final String originalString, final Iterable<Pattern> patterns, final ILoggingEvent event) {
        final StringBuilder obfuscatedStringBuilder = new StringBuilder(originalString);

        for (final Pattern pattern : patterns) {
            int currentOffset = 0;
            // Create a matcher with a copy of the current obfuscated String
            final Matcher matcher = pattern.matcher(obfuscatedStringBuilder.toString());
            while (matcher.find()) {
                for (int groupNb = 1; groupNb <= matcher.groupCount(); groupNb++) {
                    final String confidentialData = matcher.group(groupNb);

                    if (shouldObfuscate(confidentialData, event)) {
                        final String obfuscatedConfidentialData = obfuscateConfidentialData(confidentialData);

                        obfuscatedStringBuilder.replace(currentOffset + matcher.start(groupNb), currentOffset + matcher.end(groupNb), obfuscatedConfidentialData);

                        // The original String is modified in place, which will confuse the Matcher if it becomes bigger
                        if (obfuscatedConfidentialData.length() > confidentialData.length()) {
                            currentOffset += obfuscatedConfidentialData.length() - confidentialData.length();
                        }
                    }
                }
            }
        }

        return obfuscatedStringBuilder.toString();
    }

    private boolean shouldObfuscate(final String confidentialData, final ILoggingEvent event) {
        return !isProfilingHeader(confidentialData, event);
    }

    // Huge hack to avoid obfuscating the "name" in the X-Killbill-Profiling-Resp json. Unfortunately, we can't simply
    // filter-out c.s.j.a.c.filter.LoggingFilter because we do want to obfuscate requests (in case sensitive data is passed as
    // query parameters, e.g. in plugin properties)
    private boolean isProfilingHeader(final String confidentialData, final ILoggingEvent event) {
        if (!LOGGING_FILTER_NAME.equals(event.getLoggerName())) {
            return false;
        }

        for (final ProfilingFeatureType profileType : ProfilingFeatureType.values()) {
            // See ProfilingDataItem#getKey
            if (confidentialData.startsWith("\"" + profileType.name() + ":")) {
                return true;
            }
        }
        return false;
    }

    private String obfuscateConfidentialData(final CharSequence confidentialSequence) {
        return obfuscateConfidentialData(confidentialSequence, null);
    }

    /**
     * Get a mask string for masking the given `confidentialSequence`.
     *
     * @param confidentialSequence the string to be obfuscated
     * @param unmasked             the section of `confidentialSequence` to be left unmasked
     * @return a mask string
     */
    @VisibleForTesting
    String obfuscateConfidentialData(final CharSequence confidentialSequence, @Nullable final CharSequence unmasked) {
        final int maskedLength = unmasked == null ? confidentialSequence.length() : confidentialSequence.length() - unmasked.length();
        return new String(new char[maskedLength]).replace('\0', PAD_CHAR);
    }
}
