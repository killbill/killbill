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

package com.ning.billing.util.dao;

import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.entity.Entity;

public class LowerToCamelBeanMapperFactory implements ResultSetMapperFactory {

    private final Class<? extends Entity> modelClazz;

    public LowerToCamelBeanMapperFactory(final Class<? extends Entity> modelClazz) {
        this.modelClazz = modelClazz;
    }

    @Override
    public boolean accepts(final Class type, final StatementContext ctx) {
        return type.equals(modelClazz);
    }

    @Override
    public ResultSetMapper mapperFor(final Class type, final StatementContext ctx) {
        return new LowerToCamelBeanMapper(type);
    }
}
