/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.validation;

import org.killbill.billing.util.api.ColumnInfo;

public class DefaultColumnInfo implements ColumnInfo {

    private final String tableName;
    private final String columnName;
    private final Long scale;
    private final Long precision;
    private final boolean isNullable;
    private final Long maximumLength;
    private final String dataType;

    public DefaultColumnInfo(final String tableName, final String columnName, final Long scale, final Long precision,
                             final boolean nullable, final Long maximumLength, final String dataType) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.scale = scale;
        this.precision = precision;
        isNullable = nullable;
        this.maximumLength = maximumLength;
        this.dataType = dataType;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    public Long getScale() {
        return scale;
    }

    public Long getPrecision() {
        return precision;
    }

    public boolean getIsNullable() {
        return isNullable;
    }

    public Long getMaximumLength() {
        return maximumLength;
    }

    @Override
    public String getDataType() {
        return dataType;
    }
}
