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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.killbill.commons.utils.annotation.VisibleForTesting;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class PatternObfuscator extends Obfuscator {

    // Hide by default sensitive bank, PCI and PII data. For PANs, see LuhnMaskingObfuscator
    private static final String KILLBILL_OBFUSCATE_KEYWORDS_PROPERTY = "killbill.server.log.obfuscate.keywords";
    private static final String KILLBILL_OBFUSCATE_KEY_VALUE_PATTERNS_PROPERTY = "killbill.server.log.obfuscate.patterns";

    private static final String KILLBILL_OBFUSCATE_PATTERNS_SEPARATOR_PROPERTY = "killbill.server.log.obfuscate.patterns.separator";

    private final Collection<String> defaultSensitiveKeys;
    private final Collection<String> defaultStrPatterns;

    private final Collection<Pattern> patterns = new LinkedList<>();

    public PatternObfuscator() {
        this(Collections.emptyList(), Collections.emptyList());
    }

    public PatternObfuscator(final Collection<String> extraKeywords) {
        this(Collections.emptyList(), extraKeywords);
    }

    public PatternObfuscator(final Collection<Pattern> extraPatterns, final Collection<String> extraKeywords) {
        super();
        defaultSensitiveKeys = loadKeywords();
        defaultStrPatterns = loadPatterns();
        final Collection<String> keywords = new ArrayList<>(defaultSensitiveKeys);
        if (extraKeywords != null) {
            keywords.addAll(extraKeywords);
        }

        for (final String sensitiveKey : keywords) {
            this.patterns.add(buildJSONPattern(sensitiveKey));
            this.patterns.add(buildXMLPattern(sensitiveKey));
            this.patterns.add(buildMultiValuesXMLPattern(sensitiveKey));
            for (final String additionalPattern : defaultStrPatterns) {
                this.patterns.add(buildKeyValuePatterns(additionalPattern, sensitiveKey));
            }
        }
        this.patterns.addAll(extraPatterns);
    }

    @Override
    public String obfuscate(final String originalString, final ILoggingEvent event) {
        return obfuscate(originalString, patterns, event);
    }

    private Pattern buildJSONPattern(final String key) {
        return Pattern.compile(key + "\"\\s*:\\s*([^,{}]+)", DEFAULT_PATTERN_FLAGS);
    }

    private Pattern buildXMLPattern(final String key) {
        return Pattern.compile(key + "(?:\\s+.*?)?>([^<\\n]+)</[^<>]*" + key + ">", DEFAULT_PATTERN_FLAGS);
    }

    private Pattern buildMultiValuesXMLPattern(final String key) {
        return Pattern.compile(key + "</key>\\s*<value[^>]*>([^<\\n]+)</value>", DEFAULT_PATTERN_FLAGS);
    }

    private Pattern buildKeyValuePatterns(final String pattern, final String key) {
        return Pattern.compile(key + pattern, DEFAULT_PATTERN_FLAGS);
    }

    Collection<String> loadKeywords() {
        if (System.getProperty(KILLBILL_OBFUSCATE_KEYWORDS_PROPERTY) != null && !System.getProperty(KILLBILL_OBFUSCATE_KEYWORDS_PROPERTY).isEmpty()) {
            return Arrays.asList(System.getProperty(KILLBILL_OBFUSCATE_KEYWORDS_PROPERTY).split("\\s*,\\s*"));
        } else {
            return List.of(
                    "accountnumber",
                    "authenticationdata",
                    "bankaccountnumber",
                    "banknumber",
                    "bic",
                    "cardvalidationnum",
                    "cavv",
                    "ccFirstName",
                    "ccLastName",
                    "ccNumber",
                    "ccTrackData",
                    "ccVerificationValue",
                    "ccvv",
                    "cvNumber",
                    "cvc",
                    "cvv",
                    "email",
                    "iban",
                    "name",
                    "number",
                    "password",
                    "xid");
        }
    }

    @VisibleForTesting
    Collection<String> loadPatterns() {
        if (System.getProperty(KILLBILL_OBFUSCATE_KEY_VALUE_PATTERNS_PROPERTY) != null && !System.getProperty(KILLBILL_OBFUSCATE_KEY_VALUE_PATTERNS_PROPERTY).isEmpty()) {
            final String separator = loadPatternSeparator();
            return Arrays.asList(System.getProperty(KILLBILL_OBFUSCATE_KEY_VALUE_PATTERNS_PROPERTY).split("\\s*" + separator + "\\s*"));
        } else {
            return List.of("\\s*=\\s*'([^']+)'",
                           "\\s*=\\s*\"([^\"]+)\"");
        }
    }

    @VisibleForTesting
    String loadPatternSeparator() {
        if (System.getProperty(KILLBILL_OBFUSCATE_PATTERNS_SEPARATOR_PROPERTY) != null && !System.getProperty(KILLBILL_OBFUSCATE_PATTERNS_SEPARATOR_PROPERTY).isEmpty()) {
            return System.getProperty(KILLBILL_OBFUSCATE_PATTERNS_SEPARATOR_PROPERTY);
        } else {
            return ",";
        }
    }

}
