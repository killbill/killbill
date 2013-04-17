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

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleSummaryModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscription;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionEvent;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;

public class TestBusinessAnalyticsSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSqlDaoForAccount() throws Exception {
        final BusinessAccountModelDao accountModelDao = new BusinessAccountModelDao(account,
                                                                                    accountRecordId,
                                                                                    new BigDecimal("1.2345"),
                                                                                    invoice,
                                                                                    payment,
                                                                                    auditLog,
                                                                                    tenantRecordId,
                                                                                    reportGroup);

        // Check the record doesn't exist yet
        Assert.assertNull(analyticsSqlDao.getAccountByAccountRecordId(accountModelDao.getAccountRecordId(),
                                                                      accountModelDao.getTenantRecordId(),
                                                                      callContext));

        // Create and check we can retrieve it
        analyticsSqlDao.create(accountModelDao.getTableName(), accountModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getAccountByAccountRecordId(accountModelDao.getAccountRecordId(),
                                                                        accountModelDao.getTenantRecordId(),
                                                                        callContext), accountModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(accountModelDao.getTableName(),
                                                accountModelDao.getAccountRecordId(),
                                                accountModelDao.getTenantRecordId(),
                                                callContext);
        Assert.assertNull(analyticsSqlDao.getAccountByAccountRecordId(accountModelDao.getAccountRecordId(),
                                                                      accountModelDao.getTenantRecordId(),
                                                                      callContext));
    }

    @Test(groups = "slow")
    public void testSqlDaoForAccountField() throws Exception {
        final BusinessFieldModelDao businessFieldModelDao = new BusinessAccountFieldModelDao(account,
                                                                                             accountRecordId,
                                                                                             customField,
                                                                                             fieldRecordId,
                                                                                             auditLog,
                                                                                             tenantRecordId,
                                                                                             reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getAccountFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessFieldModelDao.getTableName(), businessFieldModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getAccountFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getAccountFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessFieldModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessFieldModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getAccountFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoiceField() throws Exception {
        final BusinessFieldModelDao businessFieldModelDao = new BusinessInvoiceFieldModelDao(account,
                                                                                             accountRecordId,
                                                                                             customField,
                                                                                             fieldRecordId,
                                                                                             auditLog,
                                                                                             tenantRecordId,
                                                                                             reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoiceFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessFieldModelDao.getTableName(), businessFieldModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoiceFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessFieldModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessFieldModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoicePaymentField() throws Exception {
        final BusinessFieldModelDao businessFieldModelDao = new BusinessInvoicePaymentFieldModelDao(account,
                                                                                                    accountRecordId,
                                                                                                    customField,
                                                                                                    fieldRecordId,
                                                                                                    auditLog,
                                                                                                    tenantRecordId,
                                                                                                    reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessFieldModelDao.getTableName(), businessFieldModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessFieldModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessFieldModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentFieldsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoice() throws Exception {
        final BusinessInvoiceModelDao businessInvoiceModelDao = new BusinessInvoiceModelDao(account,
                                                                                            accountRecordId,
                                                                                            invoice,
                                                                                            invoiceRecordId,
                                                                                            auditLog,
                                                                                            tenantRecordId,
                                                                                            reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessInvoiceModelDao.getTableName(), businessInvoiceModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessInvoiceModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessInvoiceModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoiceItem() throws Exception {
        final BusinessInvoiceItemBaseModelDao businessInvoiceItemModelDao = BusinessInvoiceItemBaseModelDao.create(account,
                                                                                                                   accountRecordId,
                                                                                                                   invoice,
                                                                                                                   invoiceItem,
                                                                                                                   recognizable,
                                                                                                                   // ITEM_ADJ
                                                                                                                   invoiceItemType,
                                                                                                                   invoiceItemRecordId,
                                                                                                                   secondInvoiceItemRecordId,
                                                                                                                   bundle,
                                                                                                                   plan,
                                                                                                                   phase,
                                                                                                                   auditLog,
                                                                                                                   tenantRecordId,
                                                                                                                   reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoiceItemAdjustmentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessInvoiceItemModelDao.getTableName(), businessInvoiceItemModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceItemAdjustmentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoiceItemAdjustmentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessInvoiceItemModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessInvoiceItemModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceItemAdjustmentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoicePayment() throws Exception {
        final BusinessInvoicePaymentBaseModelDao businessInvoicePaymentModelDao = BusinessInvoicePaymentModelDao.create(account,
                                                                                                                        accountRecordId,
                                                                                                                        invoice,
                                                                                                                        invoicePayment,
                                                                                                                        invoicePaymentRecordId,
                                                                                                                        payment,
                                                                                                                        refund,
                                                                                                                        paymentMethod,
                                                                                                                        auditLog,
                                                                                                                        tenantRecordId,
                                                                                                                        reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessInvoicePaymentModelDao.getTableName(), businessInvoicePaymentModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessInvoicePaymentModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessInvoicePaymentModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForOverdueStatus() throws Exception {
        final DateTime endDate = new DateTime(2005, 6, 5, 4, 5, 6, DateTimeZone.UTC);
        final BusinessOverdueStatusModelDao businessOverdueStatusModelDao = new BusinessOverdueStatusModelDao(account,
                                                                                                              accountRecordId,
                                                                                                              bundle,
                                                                                                              blockingState,
                                                                                                              blockingStateRecordId,
                                                                                                              endDate,
                                                                                                              auditLog,
                                                                                                              tenantRecordId,
                                                                                                              reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessOverdueStatusModelDao.getTableName(), businessOverdueStatusModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getOverdueStatusesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getOverdueStatusesByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessOverdueStatusModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessOverdueStatusModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getOverdueStatusesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForSubscriptionTransition() throws Exception {
        final DateTime startDate = new DateTime(2012, 6, 5, 4, 3, 12, DateTimeZone.UTC);
        final DateTime requestedTimestamp = new DateTime(2012, 7, 21, 10, 10, 10, DateTimeZone.UTC);

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf("ADD_BASE");
        final BusinessSubscription previousSubscription = null;
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, null, null, Currency.GBP, startDate, SubscriptionState.ACTIVE);
        final BusinessSubscriptionTransitionModelDao businessSubscriptionTransitionModelDao = new BusinessSubscriptionTransitionModelDao(account,
                                                                                                                                         accountRecordId,
                                                                                                                                         bundle,
                                                                                                                                         subscriptionTransition,
                                                                                                                                         subscriptionEventRecordId,
                                                                                                                                         requestedTimestamp,
                                                                                                                                         event,
                                                                                                                                         previousSubscription,
                                                                                                                                         nextSubscription,
                                                                                                                                         auditLog,
                                                                                                                                         tenantRecordId,
                                                                                                                                         reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getSubscriptionTransitionsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessSubscriptionTransitionModelDao.getTableName(), businessSubscriptionTransitionModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getSubscriptionTransitionsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getSubscriptionTransitionsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessSubscriptionTransitionModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessSubscriptionTransitionModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getSubscriptionTransitionsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForBundleSummary() throws Exception {
        final DateTime startDate = new DateTime(2012, 6, 5, 4, 3, 12, DateTimeZone.UTC);
        final DateTime requestedTimestamp = new DateTime(2012, 7, 21, 10, 10, 10, DateTimeZone.UTC);

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf("ADD_BASE");
        final BusinessSubscription previousSubscription = null;
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, null, null, Currency.GBP, startDate, SubscriptionState.ACTIVE);
        final BusinessSubscriptionTransitionModelDao businessSubscriptionTransitionModelDao = new BusinessSubscriptionTransitionModelDao(account,
                                                                                                                                         accountRecordId,
                                                                                                                                         bundle,
                                                                                                                                         subscriptionTransition,
                                                                                                                                         subscriptionEventRecordId,
                                                                                                                                         requestedTimestamp,
                                                                                                                                         event,
                                                                                                                                         previousSubscription,
                                                                                                                                         nextSubscription,
                                                                                                                                         auditLog,
                                                                                                                                         tenantRecordId,
                                                                                                                                         reportGroup);
        final BusinessBundleSummaryModelDao bundleSummaryModelDao = new BusinessBundleSummaryModelDao(account,
                                                                                                      accountRecordId,
                                                                                                      bundle,
                                                                                                      bundleRecordId,
                                                                                                      3,
                                                                                                      businessSubscriptionTransitionModelDao,
                                                                                                      auditLog,
                                                                                                      tenantRecordId,
                                                                                                      reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getBundleSummariesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(bundleSummaryModelDao.getTableName(), bundleSummaryModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getBundleSummariesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getBundleSummariesByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), bundleSummaryModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(bundleSummaryModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getBundleSummariesByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForAccountTag() throws Exception {
        final BusinessTagModelDao businessTagModelDao = new BusinessAccountTagModelDao(account,
                                                                                       accountRecordId,
                                                                                       tag,
                                                                                       tagRecordId,
                                                                                       tagDefinition,
                                                                                       auditLog,
                                                                                       tenantRecordId,
                                                                                       reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getAccountTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessTagModelDao.getTableName(), businessTagModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getAccountTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getAccountTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessTagModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessTagModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getAccountTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoiceTag() throws Exception {
        final BusinessTagModelDao businessTagModelDao = new BusinessInvoiceTagModelDao(account,
                                                                                       accountRecordId,
                                                                                       tag,
                                                                                       tagRecordId,
                                                                                       tagDefinition,
                                                                                       auditLog,
                                                                                       tenantRecordId,
                                                                                       reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoiceTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessTagModelDao.getTableName(), businessTagModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoiceTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessTagModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessTagModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoiceTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSqlDaoForInvoicePaymentTag() throws Exception {
        final BusinessTagModelDao businessTagModelDao = new BusinessInvoicePaymentTagModelDao(account,
                                                                                              accountRecordId,
                                                                                              tag,
                                                                                              tagRecordId,
                                                                                              tagDefinition,
                                                                                              auditLog,
                                                                                              tenantRecordId,
                                                                                              reportGroup);
        // Check the record doesn't exist yet
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);

        // Create and check we can retrieve it
        analyticsSqlDao.create(businessTagModelDao.getTableName(), businessTagModelDao, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 1);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).get(0), businessTagModelDao);

        // Delete and verify it doesn't exist anymore
        analyticsSqlDao.deleteByAccountRecordId(businessTagModelDao.getTableName(), accountRecordId, tenantRecordId, callContext);
        Assert.assertEquals(analyticsSqlDao.getInvoicePaymentTagsByAccountRecordId(accountRecordId, tenantRecordId, callContext).size(), 0);
    }
}
