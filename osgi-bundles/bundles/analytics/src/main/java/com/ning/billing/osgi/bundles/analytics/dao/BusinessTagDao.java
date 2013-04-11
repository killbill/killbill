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

import com.ning.billing.account.api.Account;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessTagDao extends BusinessAnalyticsDaoBase {

    public BusinessTagDao(final OSGIKillbillLogService logService,
                          final OSGIKillbillAPI osgiKillbillAPI,
                          final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void update(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final Collection<BusinessTagModelDao> tagModelDaos = createBusinessTags(account, context);

        sqlDao.inTransaction(new Transaction<Void, BusinessAnalyticsSqlDao>() {
            @Override
            public Void inTransaction(final BusinessAnalyticsSqlDao transactional, final TransactionStatus status) throws Exception {
                updateInTransaction(tagModelDaos, transactional, context);
                return null;
            }
        });
    }

    private void updateInTransaction(final Collection<BusinessTagModelDao> tagModelDaos, final BusinessAnalyticsSqlDao transactional, final CallContext context) {
        if (tagModelDaos.size() == 0) {
            return;
        }

        // We assume all tagModelDaos are for a single type
        final BusinessTagModelDao firstTagModelDao = tagModelDaos.iterator().next();
        transactional.deleteByAccountRecordId(firstTagModelDao.getTableName(), firstTagModelDao.getAccountRecordId(), firstTagModelDao.getTenantRecordId(), context);

        for (final BusinessTagModelDao tagModelDao : tagModelDaos) {
            transactional.create(tagModelDao.getTableName(), tagModelDao, context);
        }
    }

    private Collection<BusinessTagModelDao> createBusinessTags(final Account account, final CallContext context) throws AnalyticsRefreshException {
        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);

        final Collection<Tag> tags = getTagsForAccount(account.getId(), context);

        final Collection<BusinessTagModelDao> tagModelDaos = new LinkedList<BusinessTagModelDao>();
        for (final Tag tag : tags) {
            final Long tagRecordId = getTagRecordId(tag.getId(), context);
            final TagDefinition tagDefinition = getTagDefinition(tag.getTagDefinitionId(), context);
            final AuditLog creationAuditLog = getTagCreationAuditLog(tag.getId(), context);
            final BusinessTagModelDao tagModelDao = BusinessTagModelDao.create(account,
                                                                               accountRecordId,
                                                                               tag,
                                                                               tagRecordId,
                                                                               tagDefinition,
                                                                               creationAuditLog,
                                                                               tenantRecordId);
            tagModelDaos.add(tagModelDao);
        }

        return tagModelDaos;
    }
}
