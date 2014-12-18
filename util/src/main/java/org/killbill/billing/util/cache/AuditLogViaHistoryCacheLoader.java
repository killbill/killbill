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

package org.killbill.billing.util.cache;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.dao.AuditSqlDao;
import org.killbill.billing.util.dao.NonEntityDao;

import net.sf.ehcache.loader.CacheLoader;

@Singleton
public class AuditLogViaHistoryCacheLoader extends BaseCacheLoader implements CacheLoader {

    private final AuditSqlDao auditSqlDao;

    @Inject
    public AuditLogViaHistoryCacheLoader(final IDBI dbi, final NonEntityDao nonEntityDao) {
        super();
        this.auditSqlDao = dbi.onDemand(AuditSqlDao.class);
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.AUDIT_LOG_VIA_HISTORY;
    }

    @Override
    public Object load(final Object key, final Object argument) {
        checkCacheLoaderStatus();

        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Unexpected key type of " + key.getClass().getName());
        }
        if (!(argument instanceof CacheLoaderArgument)) {
            throw new IllegalArgumentException("Unexpected key type of " + argument.getClass().getName());
        }

        final Object[] args = ((CacheLoaderArgument) argument).getArgs();
        final String tableName = (String) args[0];
        final String historyTableName = (String) args[1];
        final Long targetRecordId = (Long) args[2];
        final InternalTenantContext internalTenantContext = (InternalTenantContext) args[3];

        return auditSqlDao.getAuditLogsViaHistoryForTargetRecordId(tableName, historyTableName, targetRecordId, internalTenantContext);
    }
}
