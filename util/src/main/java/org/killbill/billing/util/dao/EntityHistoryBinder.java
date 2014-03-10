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
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;

@BindingAnnotation(EntityHistoryBinder.EntityHistoryBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface EntityHistoryBinder {

    public static class EntityHistoryBinderFactory<M extends EntityModelDao<E>, E extends Entity> implements BinderFactory {

        private static final Logger logger = LoggerFactory.getLogger(EntityHistoryBinder.class);

        @Override
        public Binder build(final Annotation annotation) {
            return new Binder<EntityHistoryBinder, EntityHistoryModelDao<M, E>>() {

                @Override
                public void bind(final SQLStatement<?> q, final EntityHistoryBinder bind, final EntityHistoryModelDao<M, E> history) {
                    try {
                        // Emulate @BindBean
                        final M arg = history.getEntity();
                        final BeanInfo infos = Introspector.getBeanInfo(arg.getClass());
                        final PropertyDescriptor[] props = infos.getPropertyDescriptors();
                        for (final PropertyDescriptor prop : props) {
                            q.bind(prop.getName(), prop.getReadMethod().invoke(arg));
                        }
                        q.bind("id", history.getId());
                        q.bind("targetRecordId", history.getTargetRecordId());
                        q.bind("changeType", history.getChangeType().toString());
                    } catch (IntrospectionException e) {
                        logger.warn(e.getMessage());
                    } catch (InvocationTargetException e) {
                        logger.warn(e.getMessage());
                    } catch (IllegalAccessException e) {
                        logger.warn(e.getMessage());
                    }
                }
            };
        }
    }
}
