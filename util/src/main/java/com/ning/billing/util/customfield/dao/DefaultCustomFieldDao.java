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

import javax.annotation.Nullable;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.api.DefaultCustomFieldCreationEvent;
import com.ning.billing.util.customfield.api.DefaultCustomFieldDeletionEvent;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.dao.TagModelDao;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultCustomFieldDao extends EntityDaoBase<CustomFieldModelDao, CustomField, CustomFieldApiException> implements CustomFieldDao {

    private final static Logger log = LoggerFactory.getLogger(DefaultCustomFieldDao.class);

    private final InternalBus bus;

    @Inject
    public DefaultCustomFieldDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher controllerDispatcher, final NonEntityDao nonEntityDao, final InternalBus bus) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, controllerDispatcher, nonEntityDao), CustomFieldSqlDao.class);
        this.bus = bus;
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<CustomFieldModelDao>>() {
            @Override
            public List<CustomFieldModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class).getCustomFieldsForObject(objectId, objectType, context);
            }
        });
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context) {
        final List<CustomFieldModelDao> allFields = getCustomFieldsForAccount(context);

        return ImmutableList.<CustomFieldModelDao>copyOf(Collections2.filter(allFields, new Predicate<CustomFieldModelDao>() {
            @Override
            public boolean apply(@Nullable final CustomFieldModelDao input) {
                return input.getObjectType() == objectType;
            }
        }));
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccount(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<CustomFieldModelDao>>() {
            @Override
            public List<CustomFieldModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    protected CustomFieldApiException generateAlreadyExistsException(final CustomFieldModelDao entity, final InternalCallContext context) {
        return new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS, entity.getId());
    }

    @Override
    protected void postBusEventFromTransaction(final CustomFieldModelDao customField, final CustomFieldModelDao savedCustomField, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        BusInternalEvent customFieldEvent = null;
        switch(changeType) {
            case INSERT:
                customFieldEvent = new DefaultCustomFieldCreationEvent(customField.getId(), customField.getObjectId(), customField.getObjectType(), context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
                break;
            case DELETE:
                customFieldEvent = new DefaultCustomFieldDeletionEvent(customField.getId(), customField.getObjectId(), customField.getObjectType(), context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());

                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(customFieldEvent, entitySqlDaoWrapperFactory, context);
        } catch (InternalBus.EventBusException e) {
            log.warn("Failed to post tag event for custom field " + customField.getId().toString(), e);
        }

    }

}
