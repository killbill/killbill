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
import java.util.UUID;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.tenant.api.TenantKV;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class DefaultTenantDao implements TenantDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantDao.class);

    private final RandomNumberGenerator rng = new SecureRandomNumberGenerator();

    private final TenantSqlDao tenantSqlDao;
    private final TenantKVSqlDao tenantKVSqlDao;
    private final InternalBus eventBus;

    @Inject
    public DefaultTenantDao(final IDBI dbi, final InternalBus eventBus) {
        this.eventBus = eventBus;
        this.tenantSqlDao = dbi.onDemand(TenantSqlDao.class);
        this.tenantKVSqlDao = dbi.onDemand(TenantKVSqlDao.class);
    }

    @Override
    public Tenant getTenantByApiKey(final String apiKey) {
        return tenantSqlDao.getByApiKey(apiKey);
    }

    @Override
    public void create(final Tenant entity, final InternalCallContext context) throws EntityPersistenceException {
        // Create the salt and password
        final ByteSource salt = rng.nextBytes();
        // Hash the plain-text password with the random salt and multiple
        // iterations and then Base64-encode the value (requires less space than Hex):
        final String hashedPasswordBase64 = new Sha256Hash(entity.getApiSecret(), salt, 1024).toBase64();

        tenantSqlDao.create(entity, hashedPasswordBase64, salt.toBase64(), context);
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return tenantSqlDao.getRecordId(id.toString(), context);
    }

    @Override
    public Tenant getById(final UUID id, final InternalTenantContext context) {
        return tenantSqlDao.getById(id.toString(), context);
    }

    @Override
    public List<Tenant> get(final InternalTenantContext context) {
        return tenantSqlDao.get(context);
    }

    @Override
    public void test(final InternalTenantContext context) {
        tenantSqlDao.test(context);
    }

    @VisibleForTesting
    AuthenticationInfo getAuthenticationInfoForTenant(final UUID id) {
        return tenantSqlDao.getSecrets(id.toString()).toAuthenticationInfo();
    }

    @Override
    public String getTenantValueForKey(final UUID tenantId, final String key) {
        final TenantKV tenantKV = tenantKVSqlDao.getTenantValueForKey(tenantId.toString(), key);
        if (tenantKV == null) {
            return null;
        }
        return tenantKV.getValue();
    }

    @Override
    public void addTenantKeyValue(final UUID tenantId, final String key, final String value, final InternalCallContext context) {
        tenantKVSqlDao.insertTenantKeyValue(tenantId.toString(), key, value, context);
    }

    @Override
    public void deleteTenantKey(final UUID tenantId, final String key) {
        tenantKVSqlDao.deleteTenantKey(tenantId.toString(), key);
    }
}
