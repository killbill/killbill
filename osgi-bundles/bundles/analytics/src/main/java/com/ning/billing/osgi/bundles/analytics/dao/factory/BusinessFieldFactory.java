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

package com.ning.billing.osgi.bundles.analytics.dao.factory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class BusinessFieldFactory extends BusinessFactoryBase {

    public BusinessFieldFactory(final OSGIKillbillLogService logService,
                                final OSGIKillbillAPI osgiKillbillAPI) {
        super(logService, osgiKillbillAPI);
    }

    public Collection<BusinessFieldModelDao> createBusinessFields(final UUID accountId,
                                                                  final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final Long accountRecordId = getAccountRecordId(account.getId(), context);
        final Long tenantRecordId = getTenantRecordId(context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        final Collection<CustomField> fields = getFieldsForAccount(account.getId(), context);

        final Collection<BusinessFieldModelDao> fieldModelDaos = new LinkedList<BusinessFieldModelDao>();
        // We process custom fields sequentially: in practice, an account will be associated with a dozen fields at most
        for (final CustomField field : fields) {
            final Long customFieldRecordId = getFieldRecordId(field.getId(), context);
            final AuditLog creationAuditLog = getFieldCreationAuditLog(field.getId(), context);
            final BusinessFieldModelDao fieldModelDao = BusinessFieldModelDao.create(account,
                                                                                     accountRecordId,
                                                                                     field,
                                                                                     customFieldRecordId,
                                                                                     creationAuditLog,
                                                                                     tenantRecordId,
                                                                                     reportGroup);
            fieldModelDaos.add(fieldModelDao);
        }

        return fieldModelDaos;
    }
}
