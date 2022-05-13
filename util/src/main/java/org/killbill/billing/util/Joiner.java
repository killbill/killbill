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

import java.io.IOException;
import java.util.AbstractList;
import java.util.Iterator;

import javax.annotation.CheckForNull;

import static java.util.Objects.requireNonNull;

/**
 * Verbatim copy to guava's Joiner (v.31.0.1). See https://github.com/killbill/killbill/issues/1615
 */
public final class Joiner {

    /** Returns a joiner which automatically places {@code separator} between consecutive elements. */
    public static Joiner on(final String separator) {
        return new Joiner(separator);
    }

    /** Returns a joiner which automatically places {@code separator} between consecutive elements. */
    public static Joiner on(final char separator) {
        return new Joiner(String.valueOf(separator));
    }

    private final String separator;

    private Joiner(final String separator) {
        this.separator = Preconditions.checkNotNull(separator);
    }

    /**
     * Returns a string containing the string representation of each of {@code parts}, using the
     * previously configured separator between each.
     */
    public String join(final Iterable<?> parts) {
        return join(parts.iterator());
    }

    /**
     * Returns a string containing the string representation of each of {@code parts}, using the
     * previously configured separator between each.
     *
     * @since 11.0
     */
    public String join(final Iterator<?> parts) {
        return appendTo(new StringBuilder(), parts).toString();
    }

    /**
     * Appends the string representation of each of {@code parts}, using the previously configured
     * separator between each, to {@code builder}.
     *
     * @since 11.0
     */
    public StringBuilder appendTo(final StringBuilder builder, final Iterator<?> parts) {
        try {
            appendTo((Appendable) builder, parts);
        } catch (final IOException impossible) {
            throw new AssertionError(impossible);
        }
        return builder;
    }

    /**
     * Appends the string representation of each of {@code parts}, using the previously configured
     * separator between each, to {@code appendable}.
     *
     * @since 11.0
     */
    public <A extends Appendable> A appendTo(final A appendable, final Iterator<?> parts) throws IOException {
        Preconditions.checkNotNull(appendable);
        if (parts.hasNext()) {
            appendable.append(toString(parts.next()));
            while (parts.hasNext()) {
                appendable.append(separator);
                appendable.append(toString(parts.next()));
            }
        }
        return appendable;
    }

    CharSequence toString(@CheckForNull final Object part) {
        requireNonNull(part);
        return (part instanceof CharSequence) ? (CharSequence) part : part.toString();
    }

    /**
     * Returns a string containing the string representation of each argument, using the previously
     * configured separator between each.
     */
    public String join(@CheckForNull final Object first, @CheckForNull final Object second, final Object... rest) {
        return join(iterable(first, second, rest));
    }

    /**
     * See Guava's {@code Joiner#iterable(first, second, rest)}
     */
    private static Iterable<Object> iterable(final Object first, final Object second, final Object[] rest) {
        Preconditions.checkNotNull(rest);
        return new AbstractList<Object>() {
            @Override
            public int size() {
                return rest.length + 2;
            }

            @Override
            @CheckForNull
            public Object get(final int index) {
                switch (index) {
                    case 0:
                        return first;
                    case 1:
                        return second;
                    default:
                        return rest[index - 2];
                }
            }
        };
    }
}
