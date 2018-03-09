/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.UUID;

import javax.inject.Named;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.security.shiro.KillbillCredentialsMatcher;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultTenantDao extends EntityDaoBase<TenantModelDao, Tenant, TenantApiException> implements TenantDao {

    private final RandomNumberGenerator rng = new SecureRandomNumberGenerator();

    private final SecurityConfig securityConfig;

    @Inject
    public DefaultTenantDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                            final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory, final SecurityConfig securityConfig) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), TenantSqlDao.class);
        this.securityConfig = securityConfig;
    }

    @Override
    protected TenantApiException generateAlreadyExistsException(final TenantModelDao entity, final InternalCallContext context) {
        return new TenantApiException(ErrorCode.TENANT_ALREADY_EXISTS, entity.getExternalKey());
    }

    @Override
    public TenantModelDao getTenantByApiKey(final String apiKey) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantModelDao>() {
            @Override
            public TenantModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
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
                                                           entity.getApiSecret(), salt, securityConfig.getShiroNbHashIterations()).toBase64();

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TenantModelDao tenantModelDaoWithSecret = new TenantModelDao(entity.getId(), context.getCreatedDate(), context.getUpdatedDate(),
                                                                                   entity.getExternalKey(), entity.getApiKey(),
                                                                                   hashedPasswordBase64, salt.toBase64());
                final TenantSqlDao tenantSqlDao = entitySqlDaoWrapperFactory.become(TenantSqlDao.class);
                createAndRefresh(tenantSqlDao, tenantModelDaoWithSecret, context);
                return null;
            }
        });
    }

    @VisibleForTesting
    AuthenticationInfo getAuthenticationInfoForTenant(final UUID id) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<AuthenticationInfo>() {
            @Override
            public AuthenticationInfo inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TenantModelDao tenantModelDao = entitySqlDaoWrapperFactory.become(TenantSqlDao.class).getSecrets(id.toString());

                final SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(tenantModelDao.getApiKey(), tenantModelDao.getApiSecret().toCharArray(), getClass().getSimpleName());
                authenticationInfo.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(tenantModelDao.getApiSalt())));

                return authenticationInfo;
            }
        });
    }

    @Override
    public List<String> getTenantValueForKey(final String key, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<String>>() {
            @Override
            public List<String> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
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
    public void addTenantKeyValue(final String key, final String value, final boolean uniqueKey, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TenantKVModelDao tenantKVModelDao = new TenantKVModelDao(UUIDs.randomUUID(), context.getCreatedDate(), context.getUpdatedDate(), key, value);
                final TenantKVSqlDao tenantKVSqlDao = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class);
                if (uniqueKey) {
                    deleteFromTransaction(key, entitySqlDaoWrapperFactory, context);
                }
                final TenantKVModelDao rehydrated = createAndRefresh(tenantKVSqlDao, tenantKVModelDao, context);
                broadcastConfigurationChangeFromTransaction(rehydrated.getRecordId(), key, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    @Override
    public void updateTenantLastKeyValue(final String key, final String value, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TenantKVModelDao tenantKVModelDao = new TenantKVModelDao(UUIDs.randomUUID(), context.getCreatedDate(), context.getUpdatedDate(), key, value);
                final TenantKVSqlDao tenantKVSqlDao = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class);

                // Retrieve all values for key ordered with recordId (last at the end)
                final List<TenantKVModelDao> tenantKV = tenantKVSqlDao.getTenantValueForKey(key, context);
                final String id;
                final TenantKVModelDao rehydrated;
                if (!tenantKV.isEmpty()) {
                    id = tenantKV.get(tenantKV.size() - 1).getId().toString();
                    rehydrated = (TenantKVModelDao) tenantKVSqlDao.updateTenantValueKey(id, value, context);
                } else {
                    id = tenantKVModelDao.getId().toString();
                    rehydrated = createAndRefresh(tenantKVSqlDao, tenantKVModelDao, context);
                }
                broadcastConfigurationChangeFromTransaction(rehydrated.getRecordId(), key, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });

    }

    @Override
    public void deleteTenantKey(final String key, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                deleteFromTransaction(key, entitySqlDaoWrapperFactory, context);
                broadcastConfigurationChangeFromTransaction(null, key, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    @Override
    public TenantKVModelDao getKeyByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantKVModelDao>() {
            @Override
            public TenantKVModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public List<TenantKVModelDao> searchTenantKeyValues(final String searchKeyPrefix, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TenantKVModelDao>>() {
            @Override
            public List<TenantKVModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).searchTenantKeyValues(String.format("%s%%", searchKeyPrefix), context);
            }
        });
    }

    private Void deleteFromTransaction(final String key, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) {
        final List<TenantKVModelDao> tenantKVs = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getTenantValueForKey(key, context);
        for (TenantKVModelDao cur : tenantKVs) {
            if (cur.getTenantKey().equals(key)) {
                entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).markTenantKeyAsDeleted(cur.getId().toString(), context);
            }
        }
        return null;
    }

    private void broadcastConfigurationChangeFromTransaction(final Long kvRecordId, final String key, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                             final InternalCallContext context) throws EntityPersistenceException {
        if (isSystemKey(key)) {
            final TenantBroadcastModelDao broadcast = new TenantBroadcastModelDao(kvRecordId, key, context.getUserToken());
            final TenantBroadcastSqlDao tenantBroadcastSqlDao = entitySqlDaoWrapperFactory.become(TenantBroadcastSqlDao.class);
            createAndRefresh(tenantBroadcastSqlDao, broadcast, context);
        }
    }

    //
    // For now we restrict the caching to the (system) TenantKey keys
    //
    private boolean isSystemKey(final String key) {
        return Iterables.tryFind(ImmutableList.copyOf(TenantKey.values()), new Predicate<TenantKey>() {
            @Override
            public boolean apply(final TenantKey input) {
                return key.startsWith(input.toString());
            }
        }).orNull() != null;
    }

}
