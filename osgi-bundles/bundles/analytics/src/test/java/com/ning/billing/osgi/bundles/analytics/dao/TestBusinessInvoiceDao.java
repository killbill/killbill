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
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import javax.sql.DataSource;

import org.joda.time.LocalDate;
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
import com.ning.billing.osgi.bundles.analytics.utils.BusinessInvoiceUtils;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;

public class TestBusinessInvoiceDao extends AnalyticsTestSuiteNoDB {

    private BusinessInvoiceDao invoiceDao;

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

        invoiceDao = new BusinessInvoiceDao(osgiKillbillLogService, null, osgiKillbillDataSource);
    }

    @Test(groups = "fast")
    public void testRevenueRecognizableClassicAccountCredit() throws Exception {
        final UUID invoiceId = UUID.randomUUID();

        // Classic account credit ($10), from the perspective of the CREDIT_ADJ item
        final BusinessInvoiceItemBaseModelDao businessCreditAdjItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                           reportGroup,
                                                                                                           callContext);
        // We ignore these
        Assert.assertNull(businessCreditAdjItem);

        // Classic account credit ($10), from the perspective of the CBA_ADJ item
        final BusinessInvoiceItemBaseModelDao businessCreditItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                        reportGroup,
                                                                                                        callContext);
        // We treat these as NOT recognizable account credits
        Assert.assertEquals(businessCreditItem.getAmount().compareTo(new BigDecimal("10")), 0);
        Assert.assertEquals(businessCreditItem.getItemType(), InvoiceItemType.CBA_ADJ.toString());
        Assert.assertFalse(businessCreditItem.getRevenueRecognizable());

        // Invoice adjustment, not to be mixed with credits!
        final BusinessInvoiceItemBaseModelDao businessInvoiceAdjustmentItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                                   reportGroup,
                                                                                                                   callContext);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getItemType(), InvoiceItemType.CREDIT_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessInvoiceAdjustmentItem.getRevenueRecognizable());

        // Invoice adjustment via refund
        final BusinessInvoiceItemBaseModelDao businessRefundInvoiceAdjustmentItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                                         reportGroup,
                                                                                                                         callContext);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getItemType(), InvoiceItemType.REFUND_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessRefundInvoiceAdjustmentItem.getRevenueRecognizable());

        // Item adjustment
        final BusinessInvoiceItemBaseModelDao businessInvoiceItemAdjustmentItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                                       reportGroup,
                                                                                                                       callContext);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getItemType(), InvoiceItemType.ITEM_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessInvoiceItemAdjustmentItem.getRevenueRecognizable());

        // System generated account credit
        final BusinessInvoiceItemBaseModelDao businessCBAItem = invoiceDao.createBusinessInvoiceItem(account,
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
                                                                                                     reportGroup,
                                                                                                     callContext);
        Assert.assertEquals(businessCBAItem.getAmount().compareTo(new BigDecimal("10")), 0);
        Assert.assertEquals(businessCBAItem.getItemType(), InvoiceItemType.CBA_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessCBAItem.getRevenueRecognizable());
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

    @Test(groups = "fast")
    public void testSanitization() throws Exception {
        // One invoice, with two repairs and an external charge
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID originalInvoice1 = UUID.randomUUID();
        final UUID reparationInvoice1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(originalInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(originalInvoice1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());
        final LocalDate reparationEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal reparationAmount1 = new BigDecimal("10");
        final InvoiceItem reparation1 = createInvoiceItem(reparationInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, reparationEndDate1, reparationAmount1, null);

        final UUID subscriptionId2 = UUID.randomUUID();
        final LocalDate startDate2 = new LocalDate(2013, 4, 10);
        final LocalDate endDate2 = new LocalDate(2013, 4, 30);
        final BigDecimal amount2 = new BigDecimal("20");
        final UUID originalInvoice2 = UUID.randomUUID();
        final UUID reparationInvoice2 = UUID.randomUUID();
        final InvoiceItem recurring2 = createInvoiceItem(originalInvoice2, InvoiceItemType.RECURRING, subscriptionId2, startDate2, endDate2, amount2, null);
        final InvoiceItem repair2 = createInvoiceItem(originalInvoice2, InvoiceItemType.REPAIR_ADJ, subscriptionId2, startDate2, endDate2, amount2.negate(), recurring2.getId());
        final LocalDate reparationEndDate2 = new LocalDate(2013, 4, 15);
        final BigDecimal reparationAmount2 = new BigDecimal("5");
        final InvoiceItem reparation2 = createInvoiceItem(reparationInvoice2, InvoiceItemType.RECURRING, subscriptionId2, startDate2, reparationEndDate2, reparationAmount2, null);

        final UUID externalChargeSubscriptionId = UUID.randomUUID();
        final LocalDate externalStartDate = new LocalDate(2012, 1, 1);
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = createInvoiceItem(UUID.randomUUID(), InvoiceItemType.EXTERNAL_CHARGE, externalChargeSubscriptionId, externalStartDate, null, externalChargeAmount, null);

        final Collection<InvoiceItem> sanitizedInvoiceItems = invoiceDao.sanitizeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparation1, recurring2, repair2, reparation2, externalCharge));
        Assert.assertEquals(sanitizedInvoiceItems.size(), 2 + 2 + 1);
        for (final InvoiceItem invoiceItem : sanitizedInvoiceItems) {
            if (invoiceItem.getId().equals(recurring1.getId())) {
                Assert.assertEquals(invoiceItem, recurring1);
            } else if (invoiceItem.getId().equals(repair1.getId())) {
                if (InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()) && invoiceItem.getLinkedItemId().equals(recurring1.getId())) {
                    Assert.assertEquals(invoiceItem.getAmount(), new BigDecimal("20").negate());
                } else {
                    Assert.fail("Repair item 1 shouldn't be in the sanitized elements");
                }
            } else if (invoiceItem.getId().equals(reparation1.getId())) {
                Assert.fail("Reparation item 1 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(recurring2.getId())) {
                Assert.assertEquals(invoiceItem, recurring2);
            } else if (invoiceItem.getId().equals(repair2.getId())) {
                if (InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()) && invoiceItem.getLinkedItemId().equals(recurring2.getId())) {
                    Assert.assertEquals(invoiceItem.getAmount(), new BigDecimal("15").negate());
                } else {
                    Assert.fail("Repair item 2 shouldn't be in the sanitized elements");
                }
            } else if (invoiceItem.getId().equals(reparation2.getId())) {
                Assert.fail("Reparation item 2 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(externalCharge.getId())) {
                Assert.assertEquals(invoiceItem, externalCharge);
            } else {
                Assert.fail("Shouldn't be in the sanitized elements: " + invoiceItem);
            }
        }
    }
}
