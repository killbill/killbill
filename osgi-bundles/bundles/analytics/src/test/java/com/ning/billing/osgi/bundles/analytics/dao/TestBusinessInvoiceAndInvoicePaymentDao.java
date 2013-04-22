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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.payment.api.Payment;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;

public class TestBusinessInvoiceAndInvoicePaymentDao extends AnalyticsTestSuiteNoDB {

    private BusinessInvoiceAndInvoicePaymentDao dao;
    private OSGIKillbillAPI osgiKillbillApi;

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

        osgiKillbillApi = Mockito.mock(OSGIKillbillAPI.class, Mockito.RETURNS_DEEP_STUBS);

        final BusinessAccountDao businessAccountDao = new BusinessAccountDao(osgiKillbillLogService, osgiKillbillApi, osgiKillbillDataSource);
        dao = new BusinessInvoiceAndInvoicePaymentDao(osgiKillbillLogService, osgiKillbillApi, osgiKillbillDataSource, businessAccountDao);
    }

    @Test(groups = "fast")
    public void testVerifyDenormalizationFillUp() throws Exception {
        /*
         * Invoice 349:
	     *  +588 (recurring1)
	     *  -588 (repair)
	     *  +588 (cba)
         */
        final UUID invoice349Id = UUID.randomUUID();
        final InvoiceItem invoiceItem349Recurring1 = createInvoiceItem(invoice349Id, InvoiceItemType.RECURRING, new BigDecimal("588"));
        final InvoiceItem invoiceItem349Repair = createInvoiceItem(invoice349Id, InvoiceItemType.REPAIR_ADJ, new BigDecimal("-588"), invoiceItem349Recurring1.getId());
        final InvoiceItem invoiceItem349Cba = createInvoiceItem(invoice349Id, InvoiceItemType.CBA_ADJ, new BigDecimal("588"));
        final Invoice invoice349 = createInvoice(invoice349Id, 349, ImmutableList.<InvoiceItem>of(invoiceItem349Recurring1, invoiceItem349Repair, invoiceItem349Cba));

        final BigDecimal balance349 = BigDecimal.ZERO;
        final BigDecimal amountPaid349 = new BigDecimal("588");
        final BigDecimal amountCharged349 = new BigDecimal("27.40");
        final BigDecimal originalAmountCharged349 = new BigDecimal("588");
        final BigDecimal amountCredited349 = new BigDecimal("560.60");
        final BigDecimal amountRefunded349 = BigDecimal.ZERO;

        /*
         * Invoice 570:
	     *  +27.40 (recurring1 proration)
	     *  +42.29 (recurring2)
	     *  -69.69 (cba use)
         */
        final UUID invoice570Id = UUID.randomUUID();
        final InvoiceItem invoiceItem570Recurring1Proration = createInvoiceItem(invoice570Id,
                                                                                InvoiceItemType.RECURRING,
                                                                                invoiceItem349Recurring1.getSubscriptionId(),
                                                                                invoiceItem349Recurring1.getStartDate(),
                                                                                invoiceItem349Recurring1.getEndDate().minusDays(1),
                                                                                new BigDecimal("27.40"),
                                                                                null);
        final InvoiceItem invoiceItem570Recurring2 = createInvoiceItem(invoice570Id, InvoiceItemType.RECURRING, new BigDecimal("42.29"));
        final InvoiceItem invoiceItem570Cba = createInvoiceItem(invoice570Id, InvoiceItemType.CBA_ADJ, new BigDecimal("-69.69"));
        final Invoice invoice570 = createInvoice(invoice570Id, 570, ImmutableList.<InvoiceItem>of(invoiceItem570Recurring1Proration, invoiceItem570Recurring2, invoiceItem570Cba));

        final BigDecimal balance570 = BigDecimal.ZERO;
        final BigDecimal amountPaid570 = BigDecimal.ZERO;
        final BigDecimal amountCharged570 = new BigDecimal("42.29");
        final BigDecimal originalAmountCharged570 = new BigDecimal("42.29");
        final BigDecimal amountCredited570 = new BigDecimal("-42.29");
        final BigDecimal amountRefunded570 = BigDecimal.ZERO;

        // Setup the mocks
        // TODO this is really fragile - we need to extract a mock library for testing Kill Bill
        Mockito.when(osgiKillbillApi.getAccountUserApi().getAccountById(account.getId(), callContext)).thenReturn(account);
        Mockito.when(osgiKillbillApi.getInvoiceUserApi().getInvoicesByAccount(account.getId(), callContext)).thenReturn(ImmutableList.<Invoice>of(invoice349, invoice570));
        Mockito.when(osgiKillbillApi.getInvoiceUserApi().getInvoice(invoice349Id, callContext)).thenReturn(invoice349);
        Mockito.when(osgiKillbillApi.getInvoiceUserApi().getInvoice(invoice570Id, callContext)).thenReturn(invoice570);

        Mockito.when(payment.getAmount()).thenReturn(amountPaid349);
        Mockito.when(osgiKillbillApi.getPaymentApi().getAccountPayments(account.getId(), callContext)).thenReturn(ImmutableList.<Payment>of(payment));

        Mockito.when(invoicePayment.getInvoiceId()).thenReturn(invoice349Id);
        Mockito.when(invoicePayment.getAmount()).thenReturn(amountPaid349);
        Mockito.when(osgiKillbillApi.getInvoicePaymentApi().getInvoicePayments(payment.getId(), callContext)).thenReturn(ImmutableList.<InvoicePayment>of(invoicePayment));

        // Compute the pojos
        final Map<UUID, BusinessInvoiceModelDao> invoices = new HashMap<UUID, BusinessInvoiceModelDao>();
        final Map<UUID, Collection<BusinessInvoiceItemBaseModelDao>> invoiceItems = new HashMap<UUID, Collection<BusinessInvoiceItemBaseModelDao>>();
        final Map<UUID, Collection<BusinessInvoicePaymentBaseModelDao>> invoicePayments = new HashMap<UUID, Collection<BusinessInvoicePaymentBaseModelDao>>();
        dao.createBusinessPojos(account.getId(), invoices, invoiceItems, invoicePayments, callContext);

        /*
         * Expected Business invoice 349:
         *  BII : 	+588	(recurring1)
         *  BIIA :	-560.60
         *  BIIC:	+560.60
         *
         * Expected Business invoice 570:
         *  BII : 	+42.29	(recurring2)
         *  BIIC:	-42.29
         */
        Assert.assertEquals(invoices.keySet().size(), 2);

        Assert.assertEquals(invoices.get(invoice349Id).getBalance().compareTo(balance349), 0);
        Assert.assertEquals(invoices.get(invoice349Id).getAmountPaid().compareTo(amountPaid349), 0);
        Assert.assertEquals(invoices.get(invoice349Id).getAmountCharged().compareTo(amountCharged349), 0);
        Assert.assertEquals(invoices.get(invoice349Id).getOriginalAmountCharged().compareTo(originalAmountCharged349), 0);
        Assert.assertEquals(invoices.get(invoice349Id).getAmountCredited().compareTo(amountCredited349), 0);
        Assert.assertEquals(invoices.get(invoice349Id).getAmountRefunded().compareTo(amountRefunded349), 0);

        Assert.assertEquals(invoices.get(invoice570Id).getBalance().compareTo(balance570), 0);
        Assert.assertEquals(invoices.get(invoice570Id).getAmountPaid().compareTo(amountPaid570), 0);
        Assert.assertEquals(invoices.get(invoice570Id).getAmountCharged().compareTo(amountCharged570), 0);
        Assert.assertEquals(invoices.get(invoice570Id).getOriginalAmountCharged().compareTo(originalAmountCharged570), 0);
        Assert.assertEquals(invoices.get(invoice570Id).getAmountCredited().compareTo(amountCredited570), 0);
        Assert.assertEquals(invoices.get(invoice570Id).getAmountRefunded().compareTo(amountRefunded570), 0);

        Assert.assertEquals(invoiceItems.get(invoice349Id).size(), 3);
        for (final BusinessInvoiceItemBaseModelDao invoiceItem : invoiceItems.get(invoice349Id)) {
            if (InvoiceItemType.RECURRING.toString().equals(invoiceItem.getItemType())) {
                Assert.assertEquals(invoiceItem.getAmount().compareTo(new BigDecimal("588")), 0, String.format("RECURRING item is %s, not 588", invoiceItem.getAmount()));
            } else if (InvoiceItemType.ITEM_ADJ.toString().equals(invoiceItem.getItemType())) {
                Assert.assertEquals(invoiceItem.getAmount().compareTo(new BigDecimal("-560.60")), 0, String.format("ITEM_ADJ item is %s, not -560.60", invoiceItem.getAmount()));
            } else if (InvoiceItemType.CBA_ADJ.toString().equals(invoiceItem.getItemType())) {
                Assert.assertEquals(invoiceItem.getAmount().compareTo(new BigDecimal("560.60")), 0, String.format("CBA item is %s, not 560.60", invoiceItem.getAmount()));
            } else {
                Assert.fail();
            }
        }

        Assert.assertEquals(invoiceItems.get(invoice570Id).size(), 2);
        for (final BusinessInvoiceItemBaseModelDao invoiceItem : invoiceItems.get(invoice570Id)) {
            if (InvoiceItemType.RECURRING.toString().equals(invoiceItem.getItemType())) {
                Assert.assertEquals(invoiceItem.getAmount().compareTo(new BigDecimal("42.29")), 0, String.format("RECURRING item is %s, not 42.29", invoiceItem.getAmount()));
            } else if (InvoiceItemType.CBA_ADJ.toString().equals(invoiceItem.getItemType())) {
                Assert.assertEquals(invoiceItem.getAmount().compareTo(new BigDecimal("-42.29")), 0, String.format("CBA item is %s, not -42.29", invoiceItem.getAmount()));
            } else {
                Assert.fail();
            }
        }
    }
}
