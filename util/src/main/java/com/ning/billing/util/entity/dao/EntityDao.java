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

package com.ning.billing.util.entity.dao;

import java.util.UUID;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.Pagination;

public interface EntityDao<M extends EntityModelDao<E>, E extends Entity, U extends BillingExceptionBase> {

    public void create(M entity, InternalCallContext context) throws U;

    public Long getRecordId(UUID id, InternalTenantContext context);

    public M getByRecordId(Long recordId, InternalTenantContext context);

    public M getById(UUID id, InternalTenantContext context);

    public Pagination<M> getAll(InternalTenantContext context);

    public Pagination<M> get(Long offset, Long rowCount, InternalTenantContext context);

    public Long getCount(InternalTenantContext context);

    public void test(InternalTenantContext context);
}
