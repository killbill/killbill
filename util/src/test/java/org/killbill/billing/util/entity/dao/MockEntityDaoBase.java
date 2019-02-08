/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.entity.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MockEntityDaoBase<M extends EntityModelDao<E>, E extends Entity, U extends BillingExceptionBase> implements EntityDao<M, E, U> {

    protected static final AtomicLong autoIncrement = new AtomicLong(1);

    protected final Map<UUID, Map<Long, M>> entities = new HashMap<UUID, Map<Long, M>>();

    @Override
    public void create(final M entity, final InternalCallContext context) throws U {
        entities.put(entity.getId(), ImmutableMap.<Long, M>of(autoIncrement.incrementAndGet(), entity));
    }

    protected Long getRecordId(final UUID id, final InternalTenantContext context) {
        return entities.get(id).keySet().iterator().next();
    }

    @Override
    public M getByRecordId(final Long recordId, final InternalTenantContext context) {
        for (final Map<Long, M> cur : entities.values()) {
            if (cur.keySet().iterator().next().equals(recordId)) {
                cur.values().iterator().next();
            }
        }
        return null;
    }

    @Override
    public M getById(final UUID id, final InternalTenantContext context) {
        return entities.get(id).values().iterator().next();
    }

    @Override
    public Pagination<M> getAll(final InternalTenantContext context) {
        final List<M> result = new ArrayList<M>();
        for (final Map<Long, M> cur : entities.values()) {
            result.add(cur.values().iterator().next());
        }
        return new DefaultPagination<M>(getCount(context), result.iterator());
    }

    @Override
    public Pagination<M> get(final Long offset, final Long limit, final InternalTenantContext context) {
        return DefaultPagination.<M>build(offset, limit, ImmutableList.<M>copyOf(getAll(context)));
    }

    @Override
    public Long getCount(final InternalTenantContext context) {
        return (long) entities.keySet().size();
    }

    public void update(final M entity, final InternalCallContext context) {
        final Long entityRecordId = getRecordId(entity.getId(), context);
        entities.get(entity.getId()).put(entityRecordId, entity);
    }

    public void delete(final M entity, final InternalCallContext context) {
        entities.remove(entity.getId());
    }

    @Override
    public void test(final InternalTenantContext context) {
    }
}
