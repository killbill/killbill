/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.billing.util.dao;

import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapper;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class EntityHistoryModelDaoMapperFactory implements ResultSetMapperFactory {

    private final Class<?> sqlObjectType;
    private final Class<?> modelClazz;

    public EntityHistoryModelDaoMapperFactory(final Class modelClazz, final Class<?> sqlObjectType) {
        this.sqlObjectType = sqlObjectType;
        this.modelClazz = modelClazz;
    }

    @Override
    public boolean accepts(final Class type, final StatementContext ctx) {
        return type.isAssignableFrom(EntityHistoryModelDao.class) && sqlObjectType.equals(ctx.getSqlObjectType());
    }

    @Override
    public ResultSetMapper mapperFor(final Class type, final StatementContext ctx) {
        return new EntityHistoryModelDaoMapper(new LowerToCamelBeanMapper(modelClazz));
    }
}

