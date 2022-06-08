/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.CheckForNull;

/**
 * Verbatim copy to guava's Strings (v.31.0.1). See https://github.com/killbill/killbill/issues/1615
 */
public final class Strings {

    public static boolean isNullOrEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Returns the given string if it is nonempty; {@code null} otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} itself if it is nonempty; {@code null} if it is empty or null
     */
    public static String emptyToNull(final String string) {
        return isNullOrEmpty(string) ? null : string;
    }

    /**
     * Do what {@link String#split(String)} do, additionally filter empty/blank string and trim it.
     * A replacement for Guava's <pre>Splitter.on(',').omitEmptyStrings().trimResults();</pre>
     */
    public static List<String> split(final String string, final String separator) {
        if (isNullOrEmpty(string)) {
            return Collections.emptyList();
        }

        return Stream.of(string.split(separator))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the given string if it is non-null; the empty string otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} itself if it is non-null; {@code ""} if it is null
     */
    public static String nullToEmpty(@CheckForNull final String string) {
        return (string == null) ? "" : string;
    }

    /**
     * Return true if {@code str} contains upper-case.
     */
    public static boolean containsUpperCase(final String str) {
        if (isNullOrEmpty(str)) {
            return false;
        }
        return !str.equals(str.toLowerCase());
    }
}
