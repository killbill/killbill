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

package org.killbill.billing.tenant.dao;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultTenantBroadcastDao extends EntityDaoBase<TenantBroadcastModelDao, Entity, TenantApiException> implements TenantBroadcastDao {

    @Inject
    public DefaultTenantBroadcastDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                                     final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), TenantBroadcastSqlDao.class);
    }

    @Override
    protected TenantApiException generateAlreadyExistsException(final TenantBroadcastModelDao entity, final InternalCallContext context) {
        // STEPH generateAlreadyExistsException
        return null;
    }

    @Override
    public List<TenantBroadcastModelDao> getLatestEntriesFrom(final Long recordId) {
        throw new IllegalStateException("Not implemented by DefaultTenantBroadcastDao");
    }

    @Override
    public TenantBroadcastModelDao getLatestEntry() {
        throw new IllegalStateException("Not implemented by DefaultTenantBroadcastDao");
    }
}
