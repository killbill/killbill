/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.util.validation;

public class ColumnInfo {
    private final String tableName;
    private final String columnName;
    private final int scale;
    private final int precision;
    private final boolean isNullable;

    public ColumnInfo(String tableName, String columnName, int scale, int precision, boolean nullable) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.scale = scale;
        this.precision = precision;
        isNullable = nullable;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public int getScale() {
        return scale;
    }

    public int getPrecision() {
        return precision;
    }

    public boolean getIsNullable() {
        return isNullable;
    }
}