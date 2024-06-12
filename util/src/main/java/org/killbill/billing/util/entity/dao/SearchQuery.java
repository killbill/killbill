/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.util.entity.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

public class SearchQuery {

    public static final String SEARCH_QUERY_MARKER = "_q=1&";

    private final Map<String, Object> bindMap = new HashMap<>();
    private final List<SearchAttribute> attributes = new LinkedList<>();
    private final SqlOperator sqlOperator;
    private int globalIdx = 0;

    /**
     * @param sqlOperator the logical operator between clauses (AND or OR)
     */
    public SearchQuery(final SqlOperator sqlOperator) {
        this.sqlOperator = sqlOperator;
    }

    /**
     * @param searchQuery the raw search query
     * @param allowList   the list of allowed columns to search on
     */
    public SearchQuery(final String searchQuery,
                       final Collection<String> allowList) {
        this(SqlOperator.AND);

        final String[] params = searchQuery.split("&");
        for (final String param : params) {
            final String[] parts = param.split("=");
            final String[] columnParts = parts[0].split("\\[|\\]");
            final String column = columnParts[0];
            if (!allowList.contains(column)) {
                continue;
            }

            final String operator = columnParts.length > 1 ? columnParts[1] : null;
            final String value = parts.length > 1 ? parts[1] : null;
            // Duck typing... This might not always work. To do better, we'd need to have the caller add the type in the allowList (Map column name -> type)
            final Object valueTyped = Objects.requireNonNullElse(convertToNumber(value), value);
            addSearchClause(column, operator == null ? SqlOperator.EQ : SqlOperator.valueOf(operator.toUpperCase(Locale.ROOT)), valueTyped);
        }
    }

    private static Number convertToNumber(@Nullable final String str) {
        if (str == null) {
            return null;
        }

        try {
            // Try parsing the string as an integer
            return Integer.parseInt(str);
        } catch (final NumberFormatException e1) {
            try {
                // Try parsing the string as a double
                return Double.parseDouble(str);
            } catch (final NumberFormatException e2) {
                // String cannot be parsed as a number
                return null;
            }
        }
    }

    public void addSearchClause(final String column,
                                final SqlOperator operator,
                                final Object bindingValue) {
        // Must be unique in case of multiple conditions for a given column, e.g. WHERE age >= 18 AND age < 21
        final String bindingKey = "s_attr_" + column + globalIdx;
        globalIdx += 1;

        bindMap.put(bindingKey, bindingValue);
        attributes.add(new SearchAttribute(column, operator, bindingKey));
    }

    public Map<String, Object> getSearchKeysBindMap() {
        return bindMap;
    }

    public List<SearchAttribute> getSearchAttributes() {
        return attributes;
    }

    public SqlOperator getLogicalOperator() {
        return sqlOperator;
    }
}
