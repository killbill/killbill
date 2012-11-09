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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.Entity;

import com.google.common.collect.ImmutableMap;

public class MockEntityDaoBase<T extends Entity, U extends BillingExceptionBase> implements EntityDao<T, U> {

    protected static final AtomicLong autoIncrement = new AtomicLong(1);

    protected final Map<UUID, Map<Long, T>> entities = new HashMap<UUID, Map<Long, T>>();

    @Override
    public void create(final T entity, final InternalCallContext context) throws U {
        entities.put(entity.getId(), ImmutableMap.<Long, T>of(autoIncrement.incrementAndGet(), entity));
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return entities.get(id).keySet().iterator().next();
    }

    @Override
    public T getByRecordId(final Long recordId, final InternalTenantContext context) {
        for (final Map<Long, T> cur : entities.values()) {
            if (cur.keySet().iterator().next().equals(recordId)) {
                cur.values().iterator().next();
            }
        }
        return null;
    }

    @Override
    public T getById(final UUID id, final InternalTenantContext context) {
        return entities.get(id).values().iterator().next();
    }

    @Override
    public List<T> get(final InternalTenantContext context) {
        final List<T> result = new ArrayList<T>();
        for (final Map<Long, T> cur : entities.values()) {
            result.add(cur.values().iterator().next());
        }
        return result;
    }

    public void delete(final T entity, final InternalCallContext context) {
        entities.remove(entity.getId());
    }

    @Override
    public void test(final InternalTenantContext context) {
    }
}
