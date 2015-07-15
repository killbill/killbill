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

package org.killbill.billing.util.validation;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.killbill.billing.util.validation.dao.DatabaseSchemaDao;

import com.google.inject.Inject;

public class ValidationManager {

    private final DatabaseSchemaDao dao;

    // table name, string name, column info
    private final Map<String, Map<String, DefaultColumnInfo>> columnInfoMap = new HashMap<String, Map<String, DefaultColumnInfo>>();
    private final Map<Class, ValidationConfiguration> configurations = new HashMap<Class, ValidationConfiguration>();

    @Inject
    public ValidationManager(final DatabaseSchemaDao dao) {
        this.dao = dao;
    }

    // replaces existing schema information with the information for the specified schema
    public void loadSchemaInformation(final String schemaName) {
        columnInfoMap.clear();

        // get schema information and map it to columnInfo
        final List<DefaultColumnInfo> columnInfoList = dao.getColumnInfoList(schemaName);
        for (final DefaultColumnInfo columnInfo : columnInfoList) {
            final String tableName = columnInfo.getTableName();

            if (!columnInfoMap.containsKey(tableName)) {
                columnInfoMap.put(tableName, new HashMap<String, DefaultColumnInfo>());
            }

            columnInfoMap.get(tableName).put(columnInfo.getColumnName(), columnInfo);
        }
    }

    public Collection<DefaultColumnInfo> getTableInfo(final String tableName) {
        return columnInfoMap.get(tableName).values();
    }

    public DefaultColumnInfo getColumnInfo(final String tableName, final String columnName) {
        return (columnInfoMap.get(tableName) == null) ? null : columnInfoMap.get(tableName).get(columnName);
    }

    public boolean validate(final Object o) {
        final ValidationConfiguration configuration = getConfiguration(o.getClass());

        // if no configuration exists for this class, the object is valid
        if (configuration == null) {
            return true;
        }

        final Class clazz = o.getClass();
        for (final String propertyName : configuration.keySet()) {
            try {
                final Field field = clazz.getDeclaredField(propertyName);
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                final Object value = field.get(o);

                final DefaultColumnInfo columnInfo = configuration.get(propertyName);
                if (columnInfo == null) {
                    // no column info means the property hasn't been properly mapped; suppress validation
                    return true;
                }

                if (!hasValidNullability(columnInfo, value)) {
                    return false;
                }
                if (!isValidLengthString(columnInfo, value)) {
                    return false;
                }
                if (!isValidLengthChar(columnInfo, value)) {
                    return false;
                }
                if (!hasValidPrecision(columnInfo, value)) {
                    return false;
                }
                if (!hasValidScale(columnInfo, value)) {
                    return false;
                }
            } catch (NoSuchFieldException e) {
                // if the field doesn't exist, assume the configuration is faulty and skip this property
            } catch (IllegalAccessException e) {
                // TODO: something? deliberate no op?
            }

        }

        return true;
    }

    private boolean hasValidNullability(final DefaultColumnInfo columnInfo, final Object value) {
        if (!columnInfo.getIsNullable()) {
            if (value == null) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidLengthString(final DefaultColumnInfo columnInfo, final Object value) {
        if (columnInfo.getMaximumLength() != 0) {
            // H2 will report a character_maximum_length for decimal
            if (value != null && value instanceof String) {
                if (value.toString().length() > columnInfo.getMaximumLength()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isValidLengthChar(final DefaultColumnInfo columnInfo, final Object value) {
        // MySQL and PostgreSQL report data_type as Strings, H2 as SQLTypes
        if ("char".equals(columnInfo.getDataType()) || "character".equals(columnInfo.getDataType()) || String.valueOf(Types.CHAR).equals(columnInfo.getDataType())) {
            if (value == null) {
                return false;
            } else {
                if (value.toString().length() != columnInfo.getMaximumLength()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean hasValidPrecision(final DefaultColumnInfo columnInfo, final Object value) {
        if (columnInfo.getPrecision() != 0) {
            // H2 will report a numeric precision for varchar columns
            if (value != null && !(value instanceof String)) {
                final BigDecimal bigDecimalValue = new BigDecimal(value.toString());
                if (bigDecimalValue.precision() > columnInfo.getPrecision()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean hasValidScale(final DefaultColumnInfo columnInfo, final Object value) {
        if (columnInfo.getScale() != 0) {
            if (value != null) {
                final BigDecimal bigDecimalValue = new BigDecimal(value.toString());
                if (bigDecimalValue.scale() > columnInfo.getScale()) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean hasConfiguration(final Class clazz) {
        return configurations.containsKey(clazz);
    }

    public ValidationConfiguration getConfiguration(final Class clazz) {
        return configurations.get(clazz);
    }

    public void setConfiguration(final Class clazz, final String propertyName, final DefaultColumnInfo columnInfo) {
        if (!configurations.containsKey(clazz)) {
            configurations.put(clazz, new ValidationConfiguration());
        }

        configurations.get(clazz).addMapping(propertyName, columnInfo);
    }
}
