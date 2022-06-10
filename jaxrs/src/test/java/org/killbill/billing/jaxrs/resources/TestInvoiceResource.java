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

package org.killbill.billing.jaxrs.resources;

import javax.servlet.http.HttpServletRequest;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class TestInvoiceResource extends JaxrsTestSuiteNoDB {

    private HttpServletRequest servletRequest;
    private InvoiceUserApi invoiceUserApi;
    private AuditUserApi auditUserApi;
    private Context context;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }
        servletRequest = mock(HttpServletRequest.class);
        invoiceUserApi = mock(InvoiceUserApi.class);
        auditUserApi = mock(AuditUserApi.class);
        context = mock(Context.class);
    }

    private InvoiceResource createInvoiceResource() {
        final InvoiceResource toSpy = new InvoiceResource(
                null,
                invoiceUserApi,
                null,
                null,
                null,
                null,
                null,
                null,
                auditUserApi,
                null,
                context
        );
        return Mockito.spy(toSpy);
    }

    @Test(groups = "fast")
    public void testGetInvoice() throws InvoiceApiException {
        final Invoice invoice = mock(Invoice.class);
        final AccountAuditLogs accountAuditLogs = mock(AccountAuditLogs.class);

        when(invoiceUserApi.getInvoice(any(), any())).thenReturn(invoice);
        when(auditUserApi.getAccountAuditLogs(any(), any(), any())).thenReturn(accountAuditLogs);

        final InvoiceResource resource = createInvoiceResource();
        resource.getInvoice(UUIDs.randomUUID(), false, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, never()).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(1)).getAccountAuditLogs(any(), any(), any());

        resource.getInvoice(UUIDs.randomUUID(), true, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, times(1)).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(2)).getAccountAuditLogs(any(), any(), any());
    }

    @Test(groups = "fast")
    public void testGetInvoiceByNumber() throws InvoiceApiException {
        final Invoice invoice = mock(Invoice.class);
        final AccountAuditLogs accountAuditLogs = mock(AccountAuditLogs.class);

        when(invoiceUserApi.getInvoiceByNumber(any(), any())).thenReturn(invoice);
        when(auditUserApi.getAccountAuditLogs(any(), any(), any())).thenReturn(accountAuditLogs);

        final InvoiceResource resource = createInvoiceResource();
        resource.getInvoiceByNumber(123, false, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, never()).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(1)).getAccountAuditLogs(any(), any(), any());

        resource.getInvoiceByNumber(123, true, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, times(1)).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(2)).getAccountAuditLogs(any(), any(), any());
    }

    @Test(groups = "fast")
    public void testGetInvoiceByItemId() throws InvoiceApiException {
        final Invoice invoice = mock(Invoice.class);
        final AccountAuditLogs accountAuditLogs = mock(AccountAuditLogs.class);

        when(invoiceUserApi.getInvoiceByInvoiceItem(any(), any())).thenReturn(invoice);
        when(auditUserApi.getAccountAuditLogs(any(), any(), any())).thenReturn(accountAuditLogs);

        final InvoiceResource resource = createInvoiceResource();
        resource.getInvoiceByItemId(UUIDs.randomUUID(), false, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, never()).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(1)).getAccountAuditLogs(any(), any(), any());

        resource.getInvoiceByItemId(UUIDs.randomUUID(), true, new AuditMode("NONE"), servletRequest);

        verify(invoiceUserApi, times(1)).getInvoiceItemsByParentInvoice(any(), any());
        verify(auditUserApi, times(2)).getAccountAuditLogs(any(), any(), any());
    }

}
