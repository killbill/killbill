/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.dao;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.google.common.base.CaseFormat;

// Identical to org.skife.jdbi.v2.BeanMapper but maps created_date to createdDate
public class LowerToCamelBeanMapper<T> implements ResultSetMapper<T> {

    private final Class<T> type;
    private final Map<String, PropertyDescriptor> properties = new HashMap<String, PropertyDescriptor>();

    public LowerToCamelBeanMapper(final Class<T> type) {
        this.type = type;
        try {
            final BeanInfo info = Introspector.getBeanInfo(type);

            for (final PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                properties.put(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, descriptor.getName()).toLowerCase(), descriptor);
            }
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public T map(final int row, final ResultSet rs, final StatementContext ctx) throws SQLException {
        final T bean;
        try {
            bean = type.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("A bean, %s, was mapped " +
                                                             "which was not instantiable", type.getName()),
                                               e);
        }

        final Class beanClass = bean.getClass();
        final ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 1; i <= metadata.getColumnCount(); ++i) {
            final String name = metadata.getColumnLabel(i).toLowerCase();

            final PropertyDescriptor descriptor = properties.get(name);

            if (descriptor != null) {
                final Class<?> type = descriptor.getPropertyType();

                Object value;

                if (type.isAssignableFrom(Boolean.class) || type.isAssignableFrom(boolean.class)) {
                    value = rs.getBoolean(i);
                } else if (type.isAssignableFrom(Byte.class) || type.isAssignableFrom(byte.class)) {
                    value = rs.getByte(i);
                } else if (type.isAssignableFrom(Short.class) || type.isAssignableFrom(short.class)) {
                    value = rs.getShort(i);
                } else if (type.isAssignableFrom(Integer.class) || type.isAssignableFrom(int.class)) {
                    value = rs.getInt(i);
                } else if (type.isAssignableFrom(Long.class) || type.isAssignableFrom(long.class)) {
                    value = rs.getLong(i);
                } else if (type.isAssignableFrom(Float.class) || type.isAssignableFrom(float.class)) {
                    value = rs.getFloat(i);
                } else if (type.isAssignableFrom(Double.class) || type.isAssignableFrom(double.class)) {
                    value = rs.getDouble(i);
                } else if (type.isAssignableFrom(BigDecimal.class)) {
                    value = rs.getBigDecimal(i);
                } else if (type.isAssignableFrom(DateTime.class)) {
                    final Timestamp timestamp = rs.getTimestamp(i);
                    value = timestamp == null ? null : new DateTime(timestamp).toDateTime(DateTimeZone.UTC);
                } else if (type.isAssignableFrom(Time.class)) {
                    value = rs.getTime(i);
                } else if (type.isAssignableFrom(LocalDate.class)) {
                    final Date date = rs.getDate(i);
                    value = date == null ? null : new LocalDate(date, DateTimeZone.UTC);
                } else if (type.isAssignableFrom(DateTimeZone.class)) {
                    final String dateTimeZoneString = rs.getString(i);
                    value = dateTimeZoneString == null ? null : DateTimeZone.forID(dateTimeZoneString);
                } else if (type.isAssignableFrom(String.class)) {
                    value = rs.getString(i);
                } else if (type.isAssignableFrom(UUID.class)) {
                    final String uuidString = rs.getString(i);
                    value = uuidString == null ? null : UUID.fromString(uuidString);
                } else if (type.isEnum()) {
                    final String enumString = rs.getString(i);
                    //noinspection unchecked
                    value = enumString == null ? null : Enum.valueOf((Class<Enum>) type, enumString);
                } else {
                    value = rs.getObject(i);
                }

                if (rs.wasNull() && !type.isPrimitive()) {
                    value = null;
                }

                try {
                    final Method writeMethod = descriptor.getWriteMethod();
                    if (writeMethod != null) {
                        writeMethod.invoke(bean, value);
                    } else {
                        final String camelCasedName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
                        final Field field = getField(beanClass, camelCasedName);
                        field.setAccessible(true); // Often private...
                        field.set(bean, value);
                    }
                } catch (NoSuchFieldException e) {
                    throw new IllegalArgumentException(String.format("Unable to find field for " +
                                                                     "property, %s", name), e);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(String.format("Unable to access setter for " +
                                                                     "property, %s", name), e);
                } catch (InvocationTargetException e) {
                    throw new IllegalArgumentException(String.format("Invocation target exception trying to " +
                                                                     "invoker setter for the %s property", name), e);
                } catch (NullPointerException e) {
                    throw new IllegalArgumentException(String.format("No appropriate method to " +
                                                                     "write value %s ", value.toString()), e);
                }
            }
        }

        return bean;
    }

    private static Field getField(final Class clazz, final String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Go up in the hierarchy
            final Class superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            } else {
                return getField(superClass, fieldName);
            }
        }
    }
}
