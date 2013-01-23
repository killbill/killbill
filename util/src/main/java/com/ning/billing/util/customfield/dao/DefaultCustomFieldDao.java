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

package com.ning.billing.util.customfield.dao;

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.inject.Inject;

public class DefaultCustomFieldDao extends EntityDaoBase<CustomFieldModelDao, CustomField, CustomFieldApiException> implements CustomFieldDao {

    @Inject
    public DefaultCustomFieldDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher controllerDispatcher, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, controllerDispatcher, nonEntityDao), CustomFieldSqlDao.class);
    }

    @Override
    public List<CustomFieldModelDao> getCustomFields(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<CustomFieldModelDao>>() {
            @Override
            public List<CustomFieldModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class).getCustomFieldsForObject(objectId, objectType, context);
            }
        });
    }

    @Override
    protected CustomFieldApiException generateAlreadyExistsException(final CustomFieldModelDao entity, final InternalCallContext context) {
        return new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS, entity.getId());
    }
}
