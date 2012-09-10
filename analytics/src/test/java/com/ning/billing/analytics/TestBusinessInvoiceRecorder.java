/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.MockCatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.clock.Clock;

public class TestBusinessInvoiceRecorder {

    private final AccountUserApi accountApi = Mockito.mock(AccountUserApi.class);
    private final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
    private final InvoiceUserApi invoiceApi = Mockito.mock(InvoiceUserApi.class);
    private final BusinessInvoiceSqlDao sqlDao = Mockito.mock(BusinessInvoiceSqlDao.class);
    private final Clock clock = Mockito.mock(Clock.class);

    @Test(groups = "fast")
    public void testShouldBeAbleToHandleNullFieldsInInvoiceItem() throws Exception {
        final BusinessInvoiceRecorder recorder = new BusinessInvoiceRecorder(accountApi, entitlementApi, invoiceApi, sqlDao, new MockCatalogService(new MockCatalog()), clock);

        final InvoiceItem invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.AUD);
        Mockito.when(invoiceItem.getEndDate()).thenReturn(new LocalDate(1200, 1, 12));
        final UUID invoiceId = UUID.randomUUID();
        Mockito.when(invoiceItem.getInvoiceId()).thenReturn(invoiceId);
        final UUID id = UUID.randomUUID();
        Mockito.when(invoiceItem.getId()).thenReturn(id);
        Mockito.when(invoiceItem.getStartDate()).thenReturn(new LocalDate(1985, 9, 10));
        Mockito.when(invoiceItem.getInvoiceItemType()).thenReturn(InvoiceItemType.CREDIT_ADJ);

        final BusinessInvoiceItem bii = recorder.createBusinessInvoiceItem(invoiceItem);
        Assert.assertNotNull(bii);
        Assert.assertEquals(bii.getAmount(), invoiceItem.getAmount());
        Assert.assertEquals(bii.getCurrency(), invoiceItem.getCurrency());
        Assert.assertEquals(bii.getEndDate(), invoiceItem.getEndDate());
        Assert.assertEquals(bii.getInvoiceId(), invoiceItem.getInvoiceId());
        Assert.assertEquals(bii.getItemId(), invoiceItem.getId());
        Assert.assertEquals(bii.getStartDate(), invoiceItem.getStartDate());
        Assert.assertEquals(bii.getItemType(), invoiceItem.getInvoiceItemType().toString());
        Assert.assertNull(bii.getBillingPeriod());
        Assert.assertNull(bii.getPhase());
        Assert.assertNull(bii.getProductCategory());
        Assert.assertNull(bii.getProductName());
        Assert.assertNull(bii.getProductType());
        Assert.assertNull(bii.getSlug());
        Assert.assertNull(bii.getExternalKey());
    }
}
