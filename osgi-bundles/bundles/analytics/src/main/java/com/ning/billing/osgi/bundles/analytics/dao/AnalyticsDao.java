/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"){} you may not use this file except in compliance with the
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
import java.util.List;
import java.util.UUID;

import com.ning.billing.osgi.bundles.analytics.api.BusinessAccount;
import com.ning.billing.osgi.bundles.analytics.api.BusinessField;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoice;
import com.ning.billing.osgi.bundles.analytics.api.BusinessInvoicePayment;
import com.ning.billing.osgi.bundles.analytics.api.BusinessOverdueStatus;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.osgi.bundles.analytics.api.BusinessTag;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class AnalyticsDao extends BusinessAnalyticsDaoBase {

    public AnalyticsDao(final OSGIKillbillLogService logService,
                        final OSGIKillbillAPI osgiKillbillAPI,
                        final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public BusinessAccount getAccountById(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final BusinessAccountModelDao businessAccountModelDao = sqlDao.getAccountByAccountRecordId(accountRecordId, tenantRecordId, context);
        if (businessAccountModelDao == null) {
            return null;
        } else {
            return new BusinessAccount(businessAccountModelDao);
        }
    }

    public Collection<BusinessSubscriptionTransition> getSubscriptionTransitionsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessSubscriptionTransitionModelDao> businessSubscriptionTransitionModelDaos = sqlDao.getSubscriptionTransitionsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    public Collection<BusinessOverdueStatus> getOverdueStatusesForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessOverdueStatusModelDao> businessOverdueStatusModelDaos = sqlDao.getOverdueStatusesByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    public Collection<BusinessInvoice> getInvoicesForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessInvoiceModelDao> businessInvoiceModelDaos = sqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, context);
        final List<BusinessInvoiceItemBaseModelDao> businessInvoiceItemModelDaos = sqlDao.getInvoiceItemsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    public Collection<BusinessInvoicePayment> getInvoicePaymentsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessInvoicePaymentBaseModelDao> businessInvoicePaymentBaseModelDaos = sqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    public Collection<BusinessField> getFieldsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessFieldModelDao> businessFieldModelDaos = sqlDao.getFieldsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    public Collection<BusinessTag> getTagsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessTagModelDao> businessTagModelDaos = sqlDao.getTagsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return null;
    }

    private Long getAccountRecordId(final UUID accountId) {
        // TODO
        return 0L;
    }

    private Long getTenantRecordId(final TenantContext context) {
        // TODO
        return 0L;
    }
}
