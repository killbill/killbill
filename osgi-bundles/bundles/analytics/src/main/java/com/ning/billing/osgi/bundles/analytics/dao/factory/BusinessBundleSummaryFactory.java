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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;

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
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class BusinessBundleSummaryFactory extends BusinessFactoryBase {

    private final Executor executor;

    public BusinessBundleSummaryFactory(final OSGIKillbillLogService logService,
                                        final OSGIKillbillAPI osgiKillbillAPI,
                                        final Executor executor) {
        super(logService, osgiKillbillAPI);
        this.executor = executor;
    }

    public Collection<BusinessBundleSummaryModelDao> createBusinessBundleSummaries(final UUID accountId,
                                                                                   final Long accountRecordId,
                                                                                   final Collection<BusinessSubscriptionTransitionModelDao> bsts,
                                                                                   final Long tenantRecordId,
                                                                                   final CallContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);
        final ReportGroup reportGroup = getReportGroup(account.getId(), context);

        final Map<UUID, Integer> rankForBundle = new LinkedHashMap<UUID, Integer>();
        final Map<UUID, BusinessSubscriptionTransitionModelDao> bstForBundle = new LinkedHashMap<UUID, BusinessSubscriptionTransitionModelDao>();
        filterBstsForBasePlans(bsts, rankForBundle, bstForBundle);

        // We fetch the bundles in parallel as these can be very large on a per account basis (@see BusinessSubscriptionTransitionFactory)
        final CompletionService<BusinessBundleSummaryModelDao> completionService = new ExecutorCompletionService<BusinessBundleSummaryModelDao>(executor);
        final Collection<BusinessBundleSummaryModelDao> bbss = new LinkedList<BusinessBundleSummaryModelDao>();
        for (final BusinessSubscriptionTransitionModelDao bst : bstForBundle.values()) {
            completionService.submit(new Callable<BusinessBundleSummaryModelDao>() {
                @Override
                public BusinessBundleSummaryModelDao call() throws Exception {
                    return buildBBS(account,
                                    accountRecordId,
                                    bst,
                                    rankForBundle.get(bst.getBundleId()),
                                    tenantRecordId,
                                    reportGroup,
                                    context);
                }
            });
        }
        for (final BusinessSubscriptionTransitionModelDao ignored : bstForBundle.values()) {
            try {
                bbss.add(completionService.take().get());
            } catch (InterruptedException e) {
                throw new AnalyticsRefreshException(e);
            } catch (ExecutionException e) {
                throw new AnalyticsRefreshException(e);
            }
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
