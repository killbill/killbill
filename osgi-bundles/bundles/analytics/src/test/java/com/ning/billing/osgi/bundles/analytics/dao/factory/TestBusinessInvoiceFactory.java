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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

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
                                                                                                               reportGroup,
                                                                                                               callContext);
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
                                                                                                            reportGroup,
                                                                                                            callContext);
        // We treat these as NOT recognizable account credits
        Assert.assertEquals(businessCreditItem.getAmount().compareTo(new BigDecimal("10")), 0);
        Assert.assertEquals(businessCreditItem.getItemType(), InvoiceItemType.CBA_ADJ.toString());
        Assert.assertFalse(businessCreditItem.getRevenueRecognizable());

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
                                                                                                                       reportGroup,
                                                                                                                       callContext);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceAdjustmentItem.getItemType(), InvoiceItemType.CREDIT_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessInvoiceAdjustmentItem.getRevenueRecognizable());

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
                                                                                                                             reportGroup,
                                                                                                                             callContext);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessRefundInvoiceAdjustmentItem.getItemType(), InvoiceItemType.REFUND_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessRefundInvoiceAdjustmentItem.getRevenueRecognizable());

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
                                                                                                                           reportGroup,
                                                                                                                           callContext);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getAmount().compareTo(new BigDecimal("-10")), 0);
        Assert.assertEquals(businessInvoiceItemAdjustmentItem.getItemType(), InvoiceItemType.ITEM_ADJ.toString());
        // Recognizable by default
        Assert.assertTrue(businessInvoiceItemAdjustmentItem.getRevenueRecognizable());

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
        final UUID otherInvoice3 = UUID.randomUUID();
        final InvoiceItem externalCharge = createInvoiceItem(otherInvoice3, InvoiceItemType.EXTERNAL_CHARGE, externalChargeSubscriptionId, externalStartDate, null, externalChargeAmount, null);

        final ArrayListMultimap<UUID, InvoiceItem> allInvoiceItems = ArrayListMultimap.<UUID, InvoiceItem>create();
        allInvoiceItems.putAll(originalInvoice1, ImmutableList.<InvoiceItem>of(recurring1, repair1, reparation1));
        allInvoiceItems.putAll(originalInvoice2, ImmutableList.<InvoiceItem>of(recurring2, repair2, reparation2));
        allInvoiceItems.put(otherInvoice3, externalCharge);
        final Collection<InvoiceItem> sanitizedInvoiceItems = invoiceFactory.sanitizeInvoiceItems(allInvoiceItems);
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

    @Test(groups = "fast")
    public void testFindReparee() throws Exception {
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID originalInvoice1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(originalInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(originalInvoice1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());

        final UUID repareeInvoice1 = UUID.randomUUID();
        final LocalDate repareeEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal repareeAmount1 = new BigDecimal("10");
        final InvoiceItem reparee1 = createInvoiceItem(repareeInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, repareeEndDate1, repareeAmount1, null);

        Assert.assertEquals(invoiceFactory.findRepareeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparee1)).size(), 1);
        Assert.assertEquals(invoiceFactory.findRepareeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparee1)).get(reparee1), repair1);
    }

    @Test(groups = "fast")
    public void testCantFindRepareeWrongSubscription() throws Exception {
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID originalInvoice1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(originalInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(originalInvoice1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());

        final UUID repareeInvoice1 = UUID.randomUUID();
        final LocalDate repareeEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal repareeAmount1 = new BigDecimal("10");
        final InvoiceItem reparee1 = createInvoiceItem(repareeInvoice1, InvoiceItemType.RECURRING, UUID.randomUUID(), startDate1, repareeEndDate1, repareeAmount1, null);

        Assert.assertEquals(invoiceFactory.findRepareeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparee1)).size(), 0);
    }

    @Test(groups = "fast")
    public void testCantFindRepareeWrongEndDate() throws Exception {
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID originalInvoice1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(originalInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(originalInvoice1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());

        final UUID repareeInvoice1 = UUID.randomUUID();
        final LocalDate repareeEndDate1 = new LocalDate(2038, 4, 10);
        final BigDecimal repareeAmount1 = new BigDecimal("10");
        final InvoiceItem reparee1 = createInvoiceItem(repareeInvoice1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, repareeEndDate1, repareeAmount1, null);

        Assert.assertEquals(invoiceFactory.findRepareeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparee1)).size(), 0);
    }

    @Test(groups = "fast")
    public void testMergeCBAsNormal() throws Exception {
        final UUID invoiceId1 = UUID.randomUUID();
        final InvoiceItem cba1 = createInvoiceItem(invoiceId1, InvoiceItemType.CBA_ADJ, BigDecimal.ONE);
        final InvoiceItem cba2 = createInvoiceItem(invoiceId1, InvoiceItemType.CBA_ADJ, BigDecimal.TEN.negate());
        final InvoiceItem charge = createInvoiceItem(invoiceId1, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("9"));

        final UUID invoiceId2 = UUID.randomUUID();
        final InvoiceItem cba3 = createInvoiceItem(invoiceId2, InvoiceItemType.CBA_ADJ, BigDecimal.ONE);

        final Multimap<UUID, InvoiceItem> allInvoiceItems = ArrayListMultimap.<UUID, InvoiceItem>create();
        allInvoiceItems.put(invoiceId1, cba1);
        allInvoiceItems.put(invoiceId1, cba2);
        allInvoiceItems.put(invoiceId1, charge);
        allInvoiceItems.put(invoiceId2, cba3);

        final Collection<AdjustedCBAInvoiceItem> adjustedCBAInvoiceItems = invoiceFactory.buildMergedCBAItems(allInvoiceItems, ImmutableMap.<InvoiceItem, InvoiceItem>of());
        Assert.assertEquals(adjustedCBAInvoiceItems.size(), 2);
        for (final AdjustedCBAInvoiceItem item : adjustedCBAInvoiceItems) {
            Assert.assertEquals(item.getAmount(), item.getInvoiceId().equals(invoiceId1) ? new BigDecimal("-9") : BigDecimal.ONE);
        }
    }

    @Test(groups = "fast")
    public void testMergeCBAsWithRepairAndPayment() throws Exception {
        /*
         * Scenario:
         *  Recurring1: +30
         *  Repair1:	-30
         *  CBA:	    +30
         */
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID invoiceId1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(invoiceId1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(invoiceId1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());
        final InvoiceItem cba1 = createInvoiceItem(invoiceId1, InvoiceItemType.CBA_ADJ, amount1);

        /*
         * Scenario:
         *  Recurring1: +10
         *  CBA use:	-10
         *  Charge:     +9
         *  CBA use:	-9
         */
        final UUID invoiceId2 = UUID.randomUUID();
        final LocalDate repareeEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal repareeAmount1 = new BigDecimal("10");
        final InvoiceItem reparee1 = createInvoiceItem(invoiceId2, InvoiceItemType.RECURRING, subscriptionId1, startDate1, repareeEndDate1, repareeAmount1, null);
        final InvoiceItem cba2 = createInvoiceItem(invoiceId2, InvoiceItemType.CBA_ADJ, repareeAmount1.negate());
        final InvoiceItem charge = createInvoiceItem(invoiceId2, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("9"));
        final InvoiceItem cba3 = createInvoiceItem(invoiceId2, InvoiceItemType.CBA_ADJ, new BigDecimal("-9"));

        final Multimap<UUID, InvoiceItem> allInvoiceItems = ArrayListMultimap.<UUID, InvoiceItem>create();
        allInvoiceItems.put(invoiceId1, recurring1);
        allInvoiceItems.put(invoiceId1, repair1);
        allInvoiceItems.put(invoiceId1, cba1);
        allInvoiceItems.put(invoiceId2, reparee1);
        allInvoiceItems.put(invoiceId2, cba2);
        allInvoiceItems.put(invoiceId2, charge);
        allInvoiceItems.put(invoiceId2, cba3);

        /*
         * Expected invoice 1:
         *  Recurring1: +30
         *  Adjustment:	-20
         *  Recurring1: +10
         *  CBA:        +20
         *
         * Expected invoice 2:
         *  Charge:  +9
         *  CBA use: -9
         */
        final Collection<AdjustedCBAInvoiceItem> adjustedCBAInvoiceItems = invoiceFactory.buildMergedCBAItems(allInvoiceItems, ImmutableMap.<InvoiceItem, InvoiceItem>of(reparee1, repair1));
        Assert.assertEquals(adjustedCBAInvoiceItems.size(), 2);
        for (final AdjustedCBAInvoiceItem item : adjustedCBAInvoiceItems) {
            Assert.assertEquals(item.getAmount(), item.getInvoiceId().equals(invoiceId1) ? new BigDecimal("20") : new BigDecimal("-9"));
        }
    }

    // TODO Should add the same test, but where the second invoice is repaired to check the blacklist stuff works as expected
    @Test(groups = "fast")
    public void testMergeCBAsWithRepairAndNoPayment() throws Exception {
        /*
         * Scenario:
         *  Recurring1: +30
         *  Repair1:	-30
         */
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final UUID invoiceId1 = UUID.randomUUID();
        final InvoiceItem recurring1 = createInvoiceItem(invoiceId1, InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(invoiceId1, InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());

        /*
         * Scenario (assume account has 9 credits):
         *  Recurring1: +10
         *  Charge:     +9
         *  CBA use:    -9
         */
        final UUID invoiceId2 = UUID.randomUUID();
        final LocalDate repareeEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal repareeAmount1 = new BigDecimal("10");
        final InvoiceItem reparee1 = createInvoiceItem(invoiceId2, InvoiceItemType.RECURRING, subscriptionId1, startDate1, repareeEndDate1, repareeAmount1, null);
        final InvoiceItem charge = createInvoiceItem(invoiceId2, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("9"));
        final InvoiceItem cba1 = createInvoiceItem(invoiceId2, InvoiceItemType.CBA_ADJ, new BigDecimal("-9"));

        final Multimap<UUID, InvoiceItem> allInvoiceItems = ArrayListMultimap.<UUID, InvoiceItem>create();
        allInvoiceItems.put(invoiceId1, recurring1);
        allInvoiceItems.put(invoiceId1, repair1);
        allInvoiceItems.put(invoiceId2, reparee1);
        allInvoiceItems.put(invoiceId2, cba1);
        allInvoiceItems.put(invoiceId2, charge);

        /*
         * Expected invoice 1:
         *  Recurring1: +30
         *  Adjustment:	-20
         *  Recurring1: +10
         *
         * Expected invoice 2:
         *  Charge:  +9
         *  CBA use: -9
         */
        final Collection<AdjustedCBAInvoiceItem> adjustedCBAInvoiceItems = invoiceFactory.buildMergedCBAItems(allInvoiceItems, ImmutableMap.<InvoiceItem, InvoiceItem>of(reparee1, repair1));
        Assert.assertEquals(adjustedCBAInvoiceItems.size(), 1);
        Assert.assertEquals(adjustedCBAInvoiceItems.iterator().next().getAmount(), new BigDecimal("-9"));
    }
}
