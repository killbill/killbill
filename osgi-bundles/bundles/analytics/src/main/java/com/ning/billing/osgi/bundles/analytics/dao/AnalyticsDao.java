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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.DBI;

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
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AnalyticsDao {

    protected final BusinessAnalyticsSqlDao sqlDao;

    public AnalyticsDao(final OSGIKillbillDataSource osgiKillbillDataSource) {
        final DBI dbi = BusinessDBIProvider.get(osgiKillbillDataSource.getDataSource());
        sqlDao = dbi.onDemand(BusinessAnalyticsSqlDao.class);
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
        return Lists.transform(businessSubscriptionTransitionModelDaos, new Function<BusinessSubscriptionTransitionModelDao, BusinessSubscriptionTransition>() {
            @Override
            public BusinessSubscriptionTransition apply(final BusinessSubscriptionTransitionModelDao input) {
                return new BusinessSubscriptionTransition(input);
            }
        });
    }

    public Collection<BusinessOverdueStatus> getOverdueStatusesForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessOverdueStatusModelDao> businessOverdueStatusModelDaos = sqlDao.getOverdueStatusesByAccountRecordId(accountRecordId, tenantRecordId, context);
        return Lists.transform(businessOverdueStatusModelDaos, new Function<BusinessOverdueStatusModelDao, BusinessOverdueStatus>() {
            @Override
            public BusinessOverdueStatus apply(final BusinessOverdueStatusModelDao input) {
                return new BusinessOverdueStatus(input);
            }
        });
    }

    public Collection<BusinessInvoice> getInvoicesForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessInvoiceItemBaseModelDao> businessInvoiceItemModelDaos = sqlDao.getInvoiceItemsByAccountRecordId(accountRecordId, tenantRecordId, context);
        final Map<UUID, List<BusinessInvoiceItemBaseModelDao>> itemsPerInvoice = new LinkedHashMap<UUID, List<BusinessInvoiceItemBaseModelDao>>();
        for (final BusinessInvoiceItemBaseModelDao businessInvoiceModelDao : businessInvoiceItemModelDaos) {
            if (itemsPerInvoice.get(businessInvoiceModelDao.getInvoiceId()) == null) {
                itemsPerInvoice.put(businessInvoiceModelDao.getInvoiceId(), new LinkedList<BusinessInvoiceItemBaseModelDao>());
            }
            itemsPerInvoice.get(businessInvoiceModelDao.getInvoiceId()).add(businessInvoiceModelDao);
        }

        final List<BusinessInvoiceModelDao> businessInvoiceModelDaos = sqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, context);
        return Lists.transform(businessInvoiceModelDaos, new Function<BusinessInvoiceModelDao, BusinessInvoice>() {
            @Override
            public BusinessInvoice apply(final BusinessInvoiceModelDao input) {
                return new BusinessInvoice(input, Objects.firstNonNull(itemsPerInvoice.get(input.getInvoiceId()), ImmutableList.<BusinessInvoiceItemBaseModelDao>of()));
            }
        });
    }

    public Collection<BusinessInvoicePayment> getInvoicePaymentsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessInvoicePaymentBaseModelDao> businessInvoicePaymentBaseModelDaos = sqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return Lists.transform(businessInvoicePaymentBaseModelDaos, new Function<BusinessInvoicePaymentBaseModelDao, BusinessInvoicePayment>() {
            @Override
            public BusinessInvoicePayment apply(final BusinessInvoicePaymentBaseModelDao input) {
                return new BusinessInvoicePayment(input);
            }
        });
    }

    public Collection<BusinessField> getFieldsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessFieldModelDao> businessFieldModelDaos = sqlDao.getFieldsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return Lists.transform(businessFieldModelDaos, new Function<BusinessFieldModelDao, BusinessField>() {
            @Override
            public BusinessField apply(final BusinessFieldModelDao input) {
                return BusinessField.create(input);
            }
        });
    }

    public Collection<BusinessTag> getTagsForAccount(final UUID accountId, final TenantContext context) {
        final Long accountRecordId = getAccountRecordId(accountId);
        final Long tenantRecordId = getTenantRecordId(context);

        final List<BusinessTagModelDao> businessTagModelDaos = sqlDao.getTagsByAccountRecordId(accountRecordId, tenantRecordId, context);
        return Lists.transform(businessTagModelDaos, new Function<BusinessTagModelDao, BusinessTag>() {
            @Override
            public BusinessTag apply(final BusinessTagModelDao input) {
                return BusinessTag.create(input);
            }
        });
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
