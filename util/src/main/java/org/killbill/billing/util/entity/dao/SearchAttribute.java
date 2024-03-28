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

public class SearchAttribute {

    private final String column;
    private final SqlOperator operator;
    private final String bindingKey;

    /**
     * @param column     table column name
     * @param operator   SQL Comparison Operator between the column and the value being search (binding value)
     * @param bindingKey the binding key in the SQL query
     */
    public SearchAttribute(final String column,
                           final SqlOperator operator,
                           final String bindingKey) {
        this.column = column;
        this.operator = operator;
        this.bindingKey = bindingKey;
    }

    public String getColumn() {
        return column;
    }

    public SqlOperator getOperator() {
        return operator;
    }

    public String getBindingKey() {
        return bindingKey;
    }

    @Override
    public String toString() {
        return "SearchAttribute{" +
               "column='" + column + '\'' +
               ", operator=" + operator +
               ", bindingKey='" + bindingKey + '\'' +
               '}';
    }
}
