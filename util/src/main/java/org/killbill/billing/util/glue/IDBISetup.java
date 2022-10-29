/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.glue;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.lifecycle.ServiceFinder;
import org.killbill.billing.util.broadcast.dao.BroadcastModelDao;
import org.killbill.billing.util.dao.AuditLogModelDaoMapper;
import org.killbill.billing.util.dao.CounterMappingsMapper;
import org.killbill.billing.util.dao.EntityHistoryModelDaoMapperFactory;
import org.killbill.billing.util.dao.RecordIdIdMappingsMapper;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.nodes.dao.NodeInfoModelDao;
import org.killbill.billing.util.security.shiro.dao.RolesPermissionsModelDao;
import org.killbill.billing.util.security.shiro.dao.SessionModelDao;
import org.killbill.billing.util.security.shiro.dao.UserModelDao;
import org.killbill.billing.util.security.shiro.dao.UserRolesModelDao;
import org.killbill.billing.util.validation.dao.DatabaseSchemaSqlDao;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import static org.killbill.billing.platform.glue.KillBillPlatformModuleBase.MAIN_RO_DATA_SOURCE_ID;

public class IDBISetup {

    // See binding in KillbillPlatformModule
    public static final String MAIN_RO_IDBI_NAMED = MAIN_RO_DATA_SOURCE_ID;

    public static List<? extends ResultSetMapperFactory> mapperFactoriesToRegister() {
        final List<ResultSetMapperFactory> mapperFactories = new ArrayList<>();
        mapperFactories.add(new LowerToCamelBeanMapperFactory(SessionModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(BroadcastModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(NodeInfoModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(UserModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(UserRolesModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(RolesPermissionsModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(BusEventModelDao.class));
        mapperFactories.add(new LowerToCamelBeanMapperFactory(NotificationEventModelDao.class));

        final ServiceFinder<EntitySqlDao> serviceFinder = new ServiceFinder<EntitySqlDao>(IDBISetup.class.getClassLoader(), EntitySqlDao.class.getName());
        for (final Class<? extends EntitySqlDao> sqlObjectType : serviceFinder.getServices()) {
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
                                    mapperFactories.add(new LowerToCamelBeanMapperFactory(modelClazz));
                                    mapperFactories.add(new EntityHistoryModelDaoMapperFactory(modelClazz, sqlObjectType));
                                }
                            }
                        }
                    }
                }
            }
        }

        return List.copyOf(mapperFactories);
    }

    public static List<? extends ResultSetMapper> mappersToRegister() {
        return List.of(new AuditLogModelDaoMapper(),
                new RecordIdIdMappingsMapper(),
                new CounterMappingsMapper(),
                new DatabaseSchemaSqlDao.ColumnInfoMapper());
    }
}
