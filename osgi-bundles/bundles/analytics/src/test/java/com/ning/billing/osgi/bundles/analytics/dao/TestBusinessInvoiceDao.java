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

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;

public class TestBusinessInvoiceDao extends AnalyticsTestSuiteNoDB {

    private final UUID accountId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();

    private OSGIKillbillDataSource osgiKillbillDataSource;
    private OSGIKillbillLogService osgiKillbillLogService;

    @Override
    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();

        osgiKillbillDataSource = Mockito.mock(OSGIKillbillDataSource.class);

        final DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(osgiKillbillDataSource.getDataSource()).thenReturn(dataSource);

        osgiKillbillLogService = Mockito.mock(OSGIKillbillLogService.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                logger.info(Arrays.toString(invocation.getArguments()));
                return null;
            }
        }).when(osgiKillbillLogService).log(Mockito.anyInt(), Mockito.anyString());
    }

    @Test(groups = "fast")
    public void testSanitization() throws Exception {
        // One invoice, with two repairs and an external charge
        final UUID subscriptionId1 = UUID.randomUUID();
        final LocalDate startDate1 = new LocalDate(2013, 4, 1);
        final LocalDate endDate1 = new LocalDate(2013, 4, 30);
        final BigDecimal amount1 = new BigDecimal("30");
        final InvoiceItem recurring1 = createInvoiceItem(InvoiceItemType.RECURRING, subscriptionId1, startDate1, endDate1, amount1, null);
        final InvoiceItem repair1 = createInvoiceItem(InvoiceItemType.REPAIR_ADJ, subscriptionId1, startDate1, endDate1, amount1.negate(), recurring1.getId());
        final LocalDate reparationEndDate1 = new LocalDate(2013, 4, 10);
        final BigDecimal reparationAmount1 = new BigDecimal("10");
        final InvoiceItem reparation1 = createInvoiceItem(InvoiceItemType.RECURRING, subscriptionId1, startDate1, reparationEndDate1, reparationAmount1, null);

        final UUID subscriptionId2 = UUID.randomUUID();
        final LocalDate startDate2 = new LocalDate(2013, 4, 10);
        final LocalDate endDate2 = new LocalDate(2013, 4, 30);
        final BigDecimal amount2 = new BigDecimal("20");
        final InvoiceItem recurring2 = createInvoiceItem(InvoiceItemType.RECURRING, subscriptionId2, startDate2, endDate2, amount2, null);
        final InvoiceItem repair2 = createInvoiceItem(InvoiceItemType.REPAIR_ADJ, subscriptionId2, startDate2, endDate2, amount2.negate(), recurring2.getId());
        final LocalDate reparationEndDate2 = new LocalDate(2013, 4, 15);
        final BigDecimal reparationAmount2 = new BigDecimal("5");
        final InvoiceItem reparation2 = createInvoiceItem(InvoiceItemType.RECURRING, subscriptionId2, startDate2, reparationEndDate2, reparationAmount2, null);

        final UUID externalChargeSubscriptionId = UUID.randomUUID();
        final LocalDate externalStartDate = new LocalDate(2012, 1, 1);
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = createInvoiceItem(InvoiceItemType.EXTERNAL_CHARGE, externalChargeSubscriptionId, externalStartDate, null, externalChargeAmount, null);

        final BusinessInvoiceDao invoiceDao = new BusinessInvoiceDao(osgiKillbillLogService, null, osgiKillbillDataSource, null);
        final Collection<InvoiceItem> sanitizedInvoiceItems = invoiceDao.sanitizeInvoiceItems(ImmutableList.<InvoiceItem>of(recurring1, repair1, reparation1, recurring2, repair2, reparation2, externalCharge));
        Assert.assertEquals(sanitizedInvoiceItems.size(), 2 + 2 + 1);
        for (final InvoiceItem invoiceItem : sanitizedInvoiceItems) {
            if (invoiceItem.getId().equals(recurring1.getId())) {
                Assert.assertEquals(invoiceItem, recurring1);
            } else if (invoiceItem.getId().equals(repair1.getId())) {
                Assert.fail("Repair item 1 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(reparation1.getId())) {
                Assert.fail("Reparation item 1 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(recurring2.getId())) {
                Assert.assertEquals(invoiceItem, recurring2);
            } else if (invoiceItem.getId().equals(repair2.getId())) {
                Assert.fail("Repair item 2 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(reparation2.getId())) {
                Assert.fail("Reparation item 2 shouldn't be in the sanitized elements");
            } else if (invoiceItem.getId().equals(externalCharge.getId())) {
                Assert.assertEquals(invoiceItem, externalCharge);
            } else {
                if (InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType())) {
                    if (invoiceItem.getLinkedItemId().equals(recurring1.getId())) {
                        Assert.assertEquals(invoiceItem.getAmount(), new BigDecimal("20").negate());
                    } else if (invoiceItem.getLinkedItemId().equals(recurring2.getId())) {
                        Assert.assertEquals(invoiceItem.getAmount(), new BigDecimal("15").negate());
                    } else {
                        Assert.fail("Shouldn't be in the sanitized elements: " + invoiceItem);
                    }
                } else {
                    Assert.fail("Shouldn't be in the sanitized elements: " + invoiceItem);
                }
            }
        }
    }

    private InvoiceItem createInvoiceItem(final InvoiceItemType invoiceItemType,
                                          final UUID subscriptionId,
                                          final LocalDate startDate,
                                          final LocalDate endDate,
                                          final BigDecimal amount,
                                          @Nullable final UUID linkedItemId) {
        final UUID invoiceItemId = UUID.randomUUID();

        final InvoiceItem invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getId()).thenReturn(invoiceItemId);
        Mockito.when(invoiceItem.getInvoiceItemType()).thenReturn(invoiceItemType);
        Mockito.when(invoiceItem.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(invoiceItem.getAccountId()).thenReturn(accountId);
        Mockito.when(invoiceItem.getStartDate()).thenReturn(startDate);
        Mockito.when(invoiceItem.getEndDate()).thenReturn(endDate);
        Mockito.when(invoiceItem.getAmount()).thenReturn(amount);
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.EUR);
        Mockito.when(invoiceItem.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getBundleId()).thenReturn(bundleId);
        Mockito.when(invoiceItem.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(invoiceItem.getPlanName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getPhaseName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getRate()).thenReturn(new BigDecimal("1203"));
        Mockito.when(invoiceItem.getLinkedItemId()).thenReturn(linkedItemId);
        Mockito.when(invoiceItem.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 51, DateTimeZone.UTC));

        return invoiceItem;
    }
}
