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

package org.killbill.billing.util.cache;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.dao.AuditSqlDao;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

@Singleton
public class AuditLogViaHistoryCacheLoader extends BaseCacheLoader<String, List<AuditLogModelDao>> {

    private final AuditSqlDao roAuditSqlDao;

    @Inject
    public AuditLogViaHistoryCacheLoader(@Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        super();
        this.roAuditSqlDao = roDbi.onDemand(AuditSqlDao.class);
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.AUDIT_LOG_VIA_HISTORY;
    }

    @Override
    public List<AuditLogModelDao> compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
        final Object[] args = cacheLoaderArgument.getArgs();
        final String tableName = (String) args[0];
        final String historyTableName = (String) args[1];
        final Long targetRecordId = (Long) args[2];
        final InternalTenantContext internalTenantContext = (InternalTenantContext) args[3];

        return roAuditSqlDao.getAuditLogsViaHistoryForTargetRecordId(tableName, historyTableName, targetRecordId, internalTenantContext);
    }
}
