/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.tenant.api.user;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultTenantUserApi implements TenantUserApi {

    private final TenantDao tenantDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final CacheController<Object, Object> tenantKVCache;

    @Inject
    public DefaultTenantUserApi(final TenantDao tenantDao, final InternalCallContextFactory internalCallContextFactory, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.tenantDao = tenantDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.tenantKVCache = cacheControllerDispatcher.getCacheController(CacheType.TENANT_KV);

    }

    @Override
    public Tenant createTenant(final TenantData data, final CallContext context) throws TenantApiException {
        final Tenant tenant = new DefaultTenant(data);

        try {
            tenantDao.create(new TenantModelDao(tenant), internalCallContextFactory.createInternalCallContext(context));
        } catch (final TenantApiException e) {
            throw new TenantApiException(e, ErrorCode.TENANT_CREATION_FAILED);
        }

        return tenant;
    }

    @Override
    public Tenant getTenantByApiKey(final String key) throws TenantApiException {
        final TenantModelDao tenant = tenantDao.getTenantByApiKey(key);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_API_KEY, key);
        }
        return new DefaultTenant(tenant);
    }

    @Override
    public Tenant getTenantById(final UUID id) throws TenantApiException {
        // TODO - API cleanup?
        final TenantModelDao tenant = tenantDao.getById(id, new InternalTenantContext(null, null));
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, id);
        }
        return new DefaultTenant(tenant);
    }

    @Override
    public List<String> getTenantValueForKey(final String key, final TenantContext context)
            throws TenantApiException {

        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        final String value = getCachedTenantValueForKey(key, internalContext);
        if (value != null) {
            return ImmutableList.<String>of(value);
        }
        return tenantDao.getTenantValueForKey(key, internalContext);
    }

    @Override
    public void addTenantKeyValue(final String key, final String value, final CallContext context)
            throws TenantApiException {
        /*
        final Tenant tenant = tenantDao.getById(tenantId, new InternalTenantContext(null, null));
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, tenantId);
        }
        */

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(context);
        final String tenantKey = getCacheKeyName(key, internalContext);
        // Invalidate tenantKVCache before we store. Multi-node invalidation will follow the TenantBroadcast pattern
        tenantKVCache.remove(tenantKey);
        tenantDao.addTenantKeyValue(key, value, isSingleValueKey(key), internalContext);
    }

    @Override
    public void deleteTenantKey(final String key, final CallContext context)
            throws TenantApiException {
        /*
        final Tenant tenant = tenantDao.getById(tenantId, new InternalTenantContext(null, null));
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, tenantId);
        }
        */

        // Invalidate tenantKVCache before we store. Multi-node invalidation will follow the TenantBroadcast pattern
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(context);
        final String tenantKey = getCacheKeyName(key, internalContext);
        tenantKVCache.remove(tenantKey);
        tenantDao.deleteTenantKey(key, internalContext);
    }

    private String getCachedTenantValueForKey(final String key, final InternalTenantContext internalContext) {

        if (!isSingleValueKey(key)) {
            return null;
        }
        final String tenantKey = getCacheKeyName(key, internalContext);
        return (String) tenantKVCache.get(tenantKey, new CacheLoaderArgument(ObjectType.TENANT_KVS));
    }

    private String getCacheKeyName(final String key, final InternalTenantContext internalContext) {
        final StringBuilder tenantKey = new StringBuilder(key);
        tenantKey.append(CacheControllerDispatcher.CACHE_KEY_SEPARATOR);
        tenantKey.append(internalContext.getTenantRecordId());
        return tenantKey.toString();
    }

    private boolean isSingleValueKey(final String key) {
        return Iterables.tryFind(ImmutableList.copyOf(TenantKey.values()), new Predicate<TenantKey>() {
            @Override
            public boolean apply(final TenantKey input) {
                return input.isSingleValue() && key.startsWith(input.toString());
            }
        }).orNull() != null;
    }
}
