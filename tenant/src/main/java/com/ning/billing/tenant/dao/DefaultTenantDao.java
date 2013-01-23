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
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ErrorCode;
import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.tenant.api.TenantApiException;
import com.ning.billing.tenant.security.KillbillCredentialsMatcher;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultTenantDao extends EntityDaoBase<TenantModelDao, Tenant, TenantApiException> implements TenantDao {

    private final RandomNumberGenerator rng = new SecureRandomNumberGenerator();

    @Inject
    public DefaultTenantDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao), TenantSqlDao.class);
    }

    @Override
    protected TenantApiException generateAlreadyExistsException(final TenantModelDao entity, final InternalCallContext context) {
        return new TenantApiException(ErrorCode.TENANT_ALREADY_EXISTS, entity.getExternalKey());
    }

    @Override
    public TenantModelDao getTenantByApiKey(final String apiKey) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<TenantModelDao>() {
            @Override
            public TenantModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantSqlDao.class).getByApiKey(apiKey);
            }
        });
    }

    @Override
    public void create(final TenantModelDao entity, final InternalCallContext context) throws TenantApiException {
        // Create the salt and password
        final ByteSource salt = rng.nextBytes();
        // Hash the plain-text password with the random salt and multiple iterations and then Base64-encode the value (requires less space than Hex)
        final String hashedPasswordBase64 = new SimpleHash(KillbillCredentialsMatcher.HASH_ALGORITHM_NAME,
                                                           entity.getApiSecret(), salt, KillbillCredentialsMatcher.HASH_ITERATIONS).toBase64();

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final TenantModelDao tenantModelDaoWithSecret = new TenantModelDao(entity.getId(), context.getCreatedDate(), context.getUpdatedDate(),
                                                                                   entity.getExternalKey(), entity.getApiKey(),
                                                                                   hashedPasswordBase64, salt.toBase64());
                entitySqlDaoWrapperFactory.become(TenantSqlDao.class).create(tenantModelDaoWithSecret, context);
                return null;
            }
        });
    }

    @VisibleForTesting
    AuthenticationInfo getAuthenticationInfoForTenant(final UUID id) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<AuthenticationInfo>() {
            @Override
            public AuthenticationInfo inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final TenantModelDao tenantModelDao = entitySqlDaoWrapperFactory.become(TenantSqlDao.class).getSecrets(id.toString());

                final SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(tenantModelDao.getApiKey(), tenantModelDao.getApiSecret().toCharArray(), getClass().getSimpleName());
                authenticationInfo.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(tenantModelDao.getApiSalt())));

                return authenticationInfo;
            }
        });
    }

    @Override
    public List<String> getTenantValueForKey(final String key, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<String>>() {
            @Override
            public List<String> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<TenantKVModelDao> tenantKV = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getTenantValueForKey(key, context);
                return ImmutableList.copyOf(Collections2.transform(tenantKV, new Function<TenantKVModelDao, String>() {
                    @Override
                    public String apply(final TenantKVModelDao in) {
                        return in.getTenantValue();
                    }
                }));
            }
        });
    }

    @Override
    public void addTenantKeyValue(final String key, final String value, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final TenantKVModelDao tenantKVModelDao = new TenantKVModelDao(UUID.randomUUID(), context.getCreatedDate(), context.getUpdatedDate(), key, value);
                entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).create(tenantKVModelDao, context);
                return null;
            }
        });
    }

    @Override
    public void deleteTenantKey(final String key, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<TenantKVModelDao> tenantKVs = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getTenantValueForKey(key, context);
                for (TenantKVModelDao cur : tenantKVs) {
                    if (cur.getTenantKey().equals(key)) {
                        entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).markTenantKeyAsDeleted(cur.getId().toString(), context);
                    }
                }
                return null;
            }
        });
    }
}
