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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.ItemSource;
import com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;

public class TestBusinessInvoiceFactory extends AnalyticsTestSuiteNoDB {

    private BusinessInvoiceFactory invoiceFactory;

    @Override
    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        super.setUp();

        final OSGIKillbillDataSource osgiKillbillDataSource = Mockito.mock(OSGIKillbillDataSource.class);

        final DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(osgiKillbillDataSource.getDataSource()).thenReturn(dataSource);

        final OSGIKillbillLogService osgiKillbillLogService = Mockito.mock(OSGIKillbillLogService.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                logger.info(Arrays.toString(invocation.getArguments()));
                return null;
            }
        }).when(osgiKillbillLogService).log(Mockito.anyInt(), Mockito.anyString());

        invoiceFactory = new BusinessInvoiceFactory(osgiKillbillLogService, null);
    }

    @Test(groups = "fast")
    public void testRevenueRecognizableClassicAccountCredit() throws Exception {
        final UUID invoiceId = UUID.randomUUID();

        // Classic account credit ($10), from the perspective of the CREDIT_ADJ item
        final BusinessInvoiceItemBaseModelDao businessCreditAdjItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                               invoice,
                                                                                                               createInvoiceItem(invoiceId, InvoiceItemType.CREDIT_ADJ, new BigDecimal("-10")),
                                                                                                               ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, new BigDecimal("10"))),
                                                                                                               null,
                                                                                                               null,
                                                                                                               null,
                                                                                                               invoiceItemRecordId,
                                                                                                               auditLog,
                                                                                                               accountRecordId,
                                                                                                               tenantRecordId,
                                                                                                               reportGroup);
        // We ignore these
        Assert.assertNull(businessCreditAdjItem);

        // Classic account credit ($10), from the perspective of the CBA_ADJ item
        final BusinessInvoiceItemBaseModelDao businessCreditItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                            invoice,
                                                                                                            createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, new BigDecimal("10")),
                                                                                                            ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.CREDIT_ADJ, new BigDecimal("-10"))),
                                                                                                            null,
                                                                                                            null,
                                                                                                            null,
                                                                                                            invoiceItemRecordId,
                                                                                                            auditLog,
                                                                                                            accountRecordId,
                                                                                                            tenantRecordId,
                                                                                                            reportGroup);
        // We treat these as NOT recognizable account credits
        Assert.assertEquals(businessCreditItem.getAmount().compareTo(new BigDecimal("10")), 0);
        Assert.assertEquals(businessCreditItem.getItemType(), InvoiceItemType.CBA_ADJ.toString());
        Assert.assertEquals(businessCreditItem.getItemSource(), ItemSource.user.toString());

        // Invoice adjustment, not to be mixed with credits!
        final BusinessInvoiceItemBaseModelDao businessInvoiceAdjustmentItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                                       invoice,
                                                                                                                       createInvoiceItem(invoiceId, InvoiceItemType.CREDIT_ADJ, new BigDecimal("-10")),
                                                                                                                       ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING, new BigDecimal("10"))),
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       invoiceItemRecordId,
                                                                                                                       auditLog,
                                                                                                                       accountRecordId,
                                                                                                                       tenantRecordId,
                                                                                                                       reportGroup);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getItemType(), InvoiceItemType.CREDIT_ADJ.toString());
        Assert.assertEquals(businessInvoiceAdjustmentItem.getItemSource(), ItemSource.user.toString());

        // Invoice adjustment via refund
        final BusinessInvoiceItemBaseModelDao businessRefundInvoiceAdjustmentItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                                             invoice,
                                                                                                                             createInvoiceItem(invoiceId, InvoiceItemType.REFUND_ADJ, new BigDecimal("-10")),
                                                                                                                             ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING, new BigDecimal("10"))),
                                                                                                                             null,
                                                                                                                             null,
                                                                                                                             null,
                                                                                                                             invoiceItemRecordId,
                                                                                                                             auditLog,
                                                                                                                             accountRecordId,
                                                                                                                             tenantRecordId,
                                                                                                                             reportGroup);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getItemType(), InvoiceItemType.REFUND_ADJ.toString());
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getItemSource(), ItemSource.user.toString());

        // Item adjustment
        final BusinessInvoiceItemBaseModelDao businessInvoiceItemAdjustmentItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                                           invoice,
                                                                                                                           createInvoiceItem(invoiceId, InvoiceItemType.ITEM_ADJ, new BigDecimal("-10")),
                                                                                                                           ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING, new BigDecimal("10"))),
                                                                                                                           null,
                                                                                                                           null,
                                                                                                                           null,
                                                                                                                           invoiceItemRecordId,
                                                                                                                           auditLog,
                                                                                                                           accountRecordId,
                                                                                                                           tenantRecordId,
                                                                                                                           reportGroup);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getItemType(), InvoiceItemType.ITEM_ADJ.toString());
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getItemSource(), ItemSource.user.toString());

        // System generated account credit
        final BusinessInvoiceItemBaseModelDao businessCBAItem = invoiceFactory.createBusinessInvoiceItem(account,
                                                                                                         invoice,
                                                                                                         createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, new BigDecimal("10")),
                                                                                                         ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING, new BigDecimal("30")),
                                                                                                                                       createInvoiceItem(invoiceId, InvoiceItemType.REPAIR_ADJ, new BigDecimal("-30")),
                                                                                                                                       createInvoiceItem(invoiceId, InvoiceItemType.RECURRING, new BigDecimal("20"))),
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         invoiceItemRecordId,
                                                                                                         auditLog,
                                                                                                         accountRecordId,
                                                                                                         tenantRecordId,
                                                                                                         reportGroup);
        Assert.assertEquals(businessCBAItem.getAmount().compareTo(new BigDecimal("10")), 0);
        Assert.assertEquals(businessCBAItem.getItemType(), InvoiceItemType.CBA_ADJ.toString());
        Assert.assertEquals(businessCBAItem.getItemSource(), BusinessInvoiceItemBaseModelDao.DEFAULT_ITEM_SOURCE);
    }

    @Test(groups = "fast")
    public void testInvoiceAdjustment() throws Exception {
        final UUID invoiceId = UUID.randomUUID();

        Assert.assertFalse(BusinessInvoiceUtils.isInvoiceAdjustmentItem(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING),
                                                                        ImmutableList.<InvoiceItem>of()));
        Assert.assertTrue(BusinessInvoiceUtils.isInvoiceAdjustmentItem(createInvoiceItem(invoiceId, InvoiceItemType.REFUND_ADJ),
                                                                       ImmutableList.<InvoiceItem>of()));

        final InvoiceItem creditAdj = createInvoiceItem(invoiceId, InvoiceItemType.CREDIT_ADJ);

        // Account credit
        Assert.assertFalse(BusinessInvoiceUtils.isInvoiceAdjustmentItem(creditAdj,
                                                                        ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, creditAdj.getAmount().negate()))));

        Assert.assertTrue(BusinessInvoiceUtils.isInvoiceAdjustmentItem(creditAdj,
                                                                       ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, creditAdj.getAmount().negate().add(BigDecimal.ONE)))));
        Assert.assertTrue(BusinessInvoiceUtils.isInvoiceAdjustmentItem(creditAdj,
                                                                       ImmutableList.<InvoiceItem>of(createInvoiceItem(invoiceId, InvoiceItemType.RECURRING),
                                                                                                     createInvoiceItem(invoiceId, InvoiceItemType.CBA_ADJ, creditAdj.getAmount().negate()))));
    }
}
