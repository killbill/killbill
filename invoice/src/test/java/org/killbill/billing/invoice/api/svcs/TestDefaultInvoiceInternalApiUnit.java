/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.api.svcs;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceApiHelper;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDefaultInvoiceInternalApiUnit extends InvoiceTestSuiteNoDB {

    private final InvoiceDao invoiceDao = mock(InvoiceDao.class);
    private final InvoiceApiHelper invoiceApiHelper = mock(InvoiceApiHelper.class);
    private final InternalCallContextFactory internalCallContextFactory = mock(InternalCallContextFactory.class);

    private DefaultInvoiceInternalApi createInvoiceInternalApi() {
        final DefaultInvoiceInternalApi toSpy = new DefaultInvoiceInternalApi(invoiceDao, invoiceApiHelper, internalCallContextFactory);
        return Mockito.spy(toSpy);
    }

    private InvoicePaymentModelDao createMockedInvoicePaymentModelDaoWithTypeAndSuccess(final InvoicePaymentType type, final Boolean success) {
        final InvoicePaymentModelDao modelDao = mock(InvoicePaymentModelDao.class);
        when(modelDao.getType()).thenReturn(type);
        when(modelDao.getSuccess()).thenReturn(success);
        return modelDao;
    }

    // Belongs to testGetInvoicePayment()
    private List<InvoicePaymentModelDao> testGetInvoicePaymentModels() {
        final List<InvoicePaymentModelDao> result = new LinkedList<>();

        final AtomicInteger paymentCookie = new AtomicInteger(1);
        List.of(InvoicePaymentType.ATTEMPT, InvoicePaymentType.ATTEMPT, InvoicePaymentType.ATTEMPT, InvoicePaymentType.REFUND)
            .forEach(type -> {
                final InvoicePaymentModelDao modelDao = createMockedInvoicePaymentModelDaoWithTypeAndSuccess(type, Boolean.TRUE);
                // Only used to make sure in test, that we get the first data from the list
                when(modelDao.getPaymentCookieId()).thenReturn(String.valueOf(paymentCookie.getAndIncrement()));
                result.add(modelDao);
            });
        // Scenario: find with CHARGED_BACK will return null because getSuccess() is FALSE
        final InvoicePaymentModelDao modelDao = createMockedInvoicePaymentModelDaoWithTypeAndSuccess(InvoicePaymentType.CHARGED_BACK, Boolean.FALSE);
        result.add(modelDao);

        return result;
    }

    @Test(groups = "fast")
    public void testGetInvoicePayment() throws InvoiceApiException {
        final List<InvoicePaymentModelDao> invoicePayments = testGetInvoicePaymentModels();
        when(invoiceDao.getInvoicePaymentsByPaymentId(any(), any())).thenReturn(invoicePayments);

        // Trick for mockito because any() is not working
        final UUID anyPaymentId = UUID.randomUUID();
        final InternalTenantContext anyCtx = mock(InternalTenantContext.class);

        final DefaultInvoiceInternalApi invoiceInternalApi = createInvoiceInternalApi();
        InvoicePayment result = invoiceInternalApi.getInvoicePayment(anyPaymentId, InvoicePaymentType.ATTEMPT, anyCtx);

        Assert.assertNotNull(result);
        Assert.assertEquals(result.isSuccess(), Boolean.TRUE);
        Assert.assertEquals(result.getType(), InvoicePaymentType.ATTEMPT);
        Assert.assertEquals(result.getPaymentCookieId(), "1"); // See comment in testGetInvoicePaymentModels()

        result = invoiceInternalApi.getInvoicePayment(anyPaymentId, InvoicePaymentType.REFUND, anyCtx);

        Assert.assertNotNull(result);
        Assert.assertEquals(result.isSuccess(), Boolean.TRUE);
        Assert.assertEquals(result.getType(), InvoicePaymentType.REFUND);

        // See comment about CHARGED_BACK type in testGetInvoicePaymentModels()
        result = invoiceInternalApi.getInvoicePayment(anyPaymentId, InvoicePaymentType.CHARGED_BACK, anyCtx);

        Assert.assertNull(result);
    }
}
