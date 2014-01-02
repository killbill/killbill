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

package com.ning.billing.util.entity.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizer;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizingAnnotation;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.StatementLocator;

import com.ning.billing.util.dao.LowerToCamelBeanMapperFactory;
import com.ning.billing.util.entity.Entity;

@SqlStatementCustomizingAnnotation(EntitySqlDaoStringTemplate.EntitySqlDaoLocatorFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EntitySqlDaoStringTemplate {

    static final String DEFAULT_VALUE = " ~ ";

    String value() default DEFAULT_VALUE;

    public static class EntitySqlDaoLocatorFactory extends UseStringTemplate3StatementLocator.LocatorFactory {

        public SqlStatementCustomizer createForType(final Annotation annotation, final Class sqlObjectType) {
            // From http://www.antlr.org/wiki/display/ST/ST+condensed+--+Templates+and+groups#STcondensed--Templatesandgroups-Withsupergroupfile:
            //     there is no mechanism for automatically loading a mentioned super-group file
            new StringTemplate3StatementLocator(EntitySqlDao.class, true, true);

            final EntitySqlDaoStringTemplate a = (EntitySqlDaoStringTemplate) annotation;
            final StatementLocator l;
            if (DEFAULT_VALUE.equals(a.value())) {
                l = new StringTemplate3StatementLocator(sqlObjectType, true, true);
            } else {
                l = new StringTemplate3StatementLocator(a.value(), true, true);
            }

            return new SqlStatementCustomizer() {
                public void apply(final SQLStatement statement) {
                    statement.setStatementLocator(l);

                    if (statement instanceof Query) {
                        final Query query = (Query) statement;

                        // Find the model class associated with this sqlObjectType (which is a SqlDao class) to register its mapper
                        // If a custom mapper is defined via @RegisterMapper, don't register our generic one
                        if (sqlObjectType.getGenericInterfaces() != null &&
                            sqlObjectType.getAnnotation(RegisterMapper.class) == null) {
                            for (int i = 0; i < sqlObjectType.getGenericInterfaces().length; i++) {
                                if (sqlObjectType.getGenericInterfaces()[i] instanceof ParameterizedType) {
                                    final ParameterizedType type = (ParameterizedType) sqlObjectType.getGenericInterfaces()[i];
                                    for (int j = 0; j < type.getActualTypeArguments().length; j++) {
                                        final Type modelType = type.getActualTypeArguments()[j];
                                        if (modelType instanceof Class) {
                                            final Class modelClazz = (Class) modelType;
                                            if (Entity.class.isAssignableFrom(modelClazz)) {
                                                query.registerMapper(new LowerToCamelBeanMapperFactory(modelClazz));
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            };
        }

        public SqlStatementCustomizer createForMethod(final Annotation annotation,
                                                      final Class sqlObjectType,
                                                      final Method method) {
            throw new UnsupportedOperationException("Not Defined on Method");
        }

        public SqlStatementCustomizer createForParameter(final Annotation annotation,
                                                         final Class sqlObjectType,
                                                         final Method method,
                                                         final Object arg) {
            throw new UnsupportedOperationException("Not defined on parameter");
        }
    }
}
