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

import com.google.common.annotations.VisibleForTesting;

public abstract class Obfuscator {

    protected static final int DEFAULT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

    protected static final String MASK_LABEL = "MASKED";
    protected static final int MASK_LABEL_LENGTH = MASK_LABEL.length();
    protected static final char PAD_CHAR = '*';
    protected static final int MASK_LOOKUPS_SIZE = 20;
    protected final String[] MASK_LOOKUPS = new String[MASK_LOOKUPS_SIZE];

    public Obfuscator() {
        for (int i = 0; i < MASK_LOOKUPS.length; i++) {
            MASK_LOOKUPS[i] = buildMask(i);
        }
    }

    public abstract String obfuscate(final String originalString);

    protected String obfuscate(final String originalString, final Iterable<Pattern> patterns) {
        final StringBuilder obfuscatedStringBuilder = new StringBuilder(originalString);

        for (final Pattern pattern : patterns) {
            int currentOffset = 0;
            // Create a matcher with a copy of the current obfuscated String
            Matcher matcher = pattern.matcher(obfuscatedStringBuilder.toString());
            while (matcher.find()) {
                for (int groupNb = 1; groupNb <= matcher.groupCount(); groupNb++) {
                    final String confidentialData = matcher.group(groupNb);
                    final String obfuscatedConfidentialData = obfuscateConfidentialData(confidentialData);
                    obfuscatedStringBuilder.replace(currentOffset + matcher.start(groupNb), currentOffset + matcher.end(groupNb), obfuscatedConfidentialData);

                    // The original String is modified in place, which will confuse the Matcher if it becomes bigger
                    if (obfuscatedConfidentialData.length() > confidentialData.length()) {
                        currentOffset += obfuscatedConfidentialData.length() - confidentialData.length();
                    }
                }
            }
        }

        return obfuscatedStringBuilder.toString();
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
        if (maskedLength < MASK_LOOKUPS_SIZE) {
            return MASK_LOOKUPS[maskedLength];
        } else {
            return buildMask(maskedLength);
        }
    }

    /**
     * Create a masking string with the given length.
     *
     * @param maskedLength obfuscated String length
     * @return a mask string
     */
    private String buildMask(final int maskedLength) {
        final int pads = maskedLength - MASK_LABEL_LENGTH;
        final StringBuilder mask = new StringBuilder(maskedLength);
        if (pads <= 0) {
            mask.append(MASK_LABEL);
        } else {
            for (int i = 0; i < pads / 2; i++) {
                mask.append(PAD_CHAR);
            }
            mask.append(MASK_LABEL);
            while (mask.length() < maskedLength) {
                mask.append(PAD_CHAR);
            }
        }
        return mask.toString();
    }
}
