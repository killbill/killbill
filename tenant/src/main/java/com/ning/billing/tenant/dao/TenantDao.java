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

package com.ning.billing.tenant.dao;

import java.util.List;

import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntityDao;

public interface TenantDao extends EntityDao<Tenant> {

    public Tenant getTenantByApiKey(final String key);

    public List<String> getTenantValueForKey(final String key, final InternalTenantContext context);

    public void addTenantKeyValue(final String key, final String value, final InternalCallContext context);

    public void deleteTenantKey(final String key, final InternalCallContext context);
}
