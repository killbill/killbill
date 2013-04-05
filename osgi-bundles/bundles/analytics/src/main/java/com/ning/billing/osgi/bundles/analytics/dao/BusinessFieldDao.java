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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessFieldDao extends BusinessAnalyticsDaoBase {

    public BusinessFieldDao(final OSGIKillbillLogService logService,
                            final OSGIKillbillAPI osgiKillbillAPI,
                            final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void update(final UUID accountId, final ObjectType objectType, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final Collection<BusinessFieldModelDao> fieldModelDaos = createBusinessFields(account, objectType, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(fieldModelDaos, transactional, context);
                return null;
            }
        });
    }

    private void updateInTransaction(final Collection<BusinessFieldModelDao> fieldModelDaos, final BusinessAnalyticsSqlDao transactional, final CallContext context) {
        if (fieldModelDaos.size() == 0) {
            return;
        }

        // We assume all fieldModelDaos are for a single type
        final BusinessFieldModelDao firstFieldModelDao = fieldModelDaos.iterator().next();
        transactional.deleteByAccountRecordId(firstFieldModelDao.getTableName(), firstFieldModelDao.getAccountRecordId(), firstFieldModelDao.getTenantRecordId(), context);

        for (final BusinessFieldModelDao fieldModelDao : fieldModelDaos) {
            transactional.create(fieldModelDao.getTableName(), fieldModelDao, context);
        }
    }

    private Collection<BusinessFieldModelDao> createBusinessFields(final Account account, final ObjectType objectType, final CallContext context) throws AnalyticsRefreshException {
        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);

        final Collection<CustomField> fields = getFieldsForAccountAndObjectType(account.getId(), objectType, context);

        final Collection<BusinessFieldModelDao> fieldModelDaos = new LinkedList<BusinessFieldModelDao>();
        for (final CustomField field : fields) {
            final Long customFieldRecordId = getFieldRecordId(field.getId(), context);
            final AuditLog creationAuditLog = getFieldCreationAuditLog(field.getId(), context);
            final BusinessFieldModelDao fieldModelDao = BusinessFieldModelDao.create(account,
                                                                                     accountRecordId,
                                                                                     field,
                                                                                     customFieldRecordId,
                                                                                     creationAuditLog,
                                                                                     tenantRecordId);
            fieldModelDaos.add(fieldModelDao);
        }

        return fieldModelDaos;
    }
}
