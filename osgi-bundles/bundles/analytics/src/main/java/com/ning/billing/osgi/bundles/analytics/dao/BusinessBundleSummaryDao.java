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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleSummaryModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class BusinessBundleSummaryDao extends BusinessAnalyticsDaoBase {

    public BusinessBundleSummaryDao(final OSGIKillbillLogService logService,
                                    final OSGIKillbillAPI osgiKillbillAPI,
                                    final OSGIKillbillDataSource osgiKillbillDataSource) {
        super(logService, osgiKillbillAPI, osgiKillbillDataSource);
    }

    public void updateInTransaction(final Collection<BusinessBundleSummaryModelDao> bbss,
                                    final Long accountRecordId,
                                    final Long tenantRecordId,
                                    final BusinessAnalyticsSqlDao transactional,
                                    final CallContext context) {
        transactional.deleteByAccountRecordId(BusinessBundleSummaryModelDao.BUNDLE_SUMMARIES_TABLE_NAME,
                                              accountRecordId,
                                              tenantRecordId,
                                              context);

        for (final BusinessBundleSummaryModelDao bbs : bbss) {
            transactional.create(bbs.getTableName(), bbs, context);
        }

        // The update of summary columns in BAC will be done via BST
    }

    public Collection<BusinessBundleSummaryModelDao> createBusinessBundleSummaries(final Account account,
                                                                                   final Long accountRecordId,
                                                                                   final Collection<BusinessSubscriptionTransitionModelDao> bsts,
                                                                                   final Long tenantRecordId,
                                                                                   final ReportGroup reportGroup,
                                                                                   final CallContext context) throws AnalyticsRefreshException {
        final Map<UUID, Integer> rankForBundle = new LinkedHashMap<UUID, Integer>();
        final Map<UUID, BusinessSubscriptionTransitionModelDao> bstForBundle = new LinkedHashMap<UUID, BusinessSubscriptionTransitionModelDao>();
        filterBstsForBasePlans(bsts, rankForBundle, bstForBundle);

        final Collection<BusinessBundleSummaryModelDao> bbss = new LinkedList<BusinessBundleSummaryModelDao>();
        for (final BusinessSubscriptionTransitionModelDao bst : bstForBundle.values()) {
            final BusinessBundleSummaryModelDao bbs = buildBBS(account,
                                                               accountRecordId,
                                                               bst,
                                                               rankForBundle.get(bst.getBundleId()),
                                                               tenantRecordId,
                                                               reportGroup,
                                                               context);
            bbss.add(bbs);
        }
        return bbss;
    }

    @VisibleForTesting
    void filterBstsForBasePlans(final Collection<BusinessSubscriptionTransitionModelDao> bsts, final Map<UUID, Integer> rankForBundle, final Map<UUID, BusinessSubscriptionTransitionModelDao> bstForBundle) {// Find bsts for BASE subscriptions only and sort them using the next start date
        final Collection<BusinessSubscriptionTransitionModelDao> sortedBundlesBst = Ordering.from(new Comparator<BusinessSubscriptionTransitionModelDao>() {
            @Override
            public int compare(final BusinessSubscriptionTransitionModelDao o1, final BusinessSubscriptionTransitionModelDao o2) {
                return o1.getNextStartDate().compareTo(o2.getNextStartDate());
            }
        }).sortedCopy(Iterables.filter(bsts, new Predicate<BusinessSubscriptionTransitionModelDao>() {
            @Override
            public boolean apply(final BusinessSubscriptionTransitionModelDao input) {
                return ProductCategory.BASE.toString().equals(input.getNextProductCategory());
            }
        }));

        UUID lastBundleId = null;
        Integer lastBundleRank = 0;
        for (final BusinessSubscriptionTransitionModelDao bst : sortedBundlesBst) {
            // Note that sortedBundlesBst is not ordered bundle by bundle, i.e. we may have:
            // bundleId1 CREATE, bundleId2 CREATE, bundleId1 PHASE, bundleId3 CREATE bundleId2 PHASE
            if (lastBundleId == null || (!lastBundleId.equals(bst.getBundleId()) && rankForBundle.get(bst.getBundleId()) == null)) {
                lastBundleRank++;
                lastBundleId = bst.getBundleId();
                rankForBundle.put(lastBundleId, lastBundleRank);
            }

            if (bstForBundle.get(bst.getBundleId()) == null ||
                bstForBundle.get(bst.getBundleId()).getNextStartDate().isBefore(bst.getNextStartDate())) {
                bstForBundle.put(bst.getBundleId(), bst);
            }
        }
    }

    private BusinessBundleSummaryModelDao buildBBS(final Account account, final Long accountRecordId, final BusinessSubscriptionTransitionModelDao bst, final Integer bundleAccountRank, final Long tenantRecordId, final ReportGroup reportGroup, final CallContext context) throws AnalyticsRefreshException {
        final SubscriptionBundle bundle = getSubscriptionBundle(bst.getBundleId(), context);
        final Long bundleRecordId = getBundleRecordId(bundle.getId(), context);
        final AuditLog creationAuditLog = getBundleCreationAuditLog(bundle.getId(), context);

        return new BusinessBundleSummaryModelDao(account,
                                                 accountRecordId,
                                                 bundle,
                                                 bundleRecordId,
                                                 bundleAccountRank,
                                                 bst,
                                                 creationAuditLog,
                                                 tenantRecordId,
                                                 reportGroup);
    }
}
