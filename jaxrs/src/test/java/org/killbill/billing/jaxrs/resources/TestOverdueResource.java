/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import javax.servlet.ServletRequest;
import javax.ws.rs.core.Response;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

import static org.mockito.Mockito.*;

public class TestOverdueResource extends TestJaxRsResourceBase {

    private final ServletRequest servletRequest = mock(ServletRequest.class);
    private final TenantContext tenantContext = mock(TenantContext.class);

    private final JaxrsUriBuilder jaxrsUriBuilder = mock(JaxrsUriBuilder.class);
    private final TagUserApi tagUserApi = mock(TagUserApi.class);
    private final CustomFieldUserApi customFieldUserApi = mock(CustomFieldUserApi.class);
    private final AuditUserApi auditUserApi = mock(AuditUserApi.class);
    private final AccountUserApi accountUserApi = mock(AccountUserApi.class);
    private final PaymentApi paymentApi = mock(PaymentApi.class);
    private final InvoicePaymentApi invoicePaymentApi = mock(InvoicePaymentApi.class);
    private final OverdueApi overdueApi = mock(OverdueApi.class);
    private final Clock clock = mock(Clock.class);
    private final Context context = mock(Context.class);


    private OverdueResource newOverdueResource() {
        return new OverdueResource(
                jaxrsUriBuilder,
                tagUserApi, customFieldUserApi, auditUserApi, accountUserApi,
                paymentApi, invoicePaymentApi, overdueApi,
                clock, context);
    }


    protected DefaultOverdueConfig getOverdueConfig(final String name) throws Exception {
        return XMLLoader.getObjectFromString(Resources.getResource("org/killbill/billing/jaxrs/resources/overdue/" + name).toExternalForm(), DefaultOverdueConfig.class);
    }

    /**
     * Represent invalid overdue that cannot marshalled into JSON. This XML contains the same value as defined in:
     * https://github.com/killbill/killbill/issues/1497
     *
     * This XML causing NullPointerException when JSON marshalling because it missing
     * 'timeSinceEarliestUnpaidInvoiceEqualsOrExceeds' element.
     */
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1497")
    public void testGetOverdueConfigJsonNoTimeSinceEarliest() throws Exception {
        final OverdueConfig overdueConfig = getOverdueConfig("overdue_noTimeSinceEarliest.xml");

        when(context.createTenantContextNoAccountId(servletRequest)).thenReturn(tenantContext);
        when(overdueApi.getOverdueConfig(any())).thenReturn(overdueConfig);

        final OverdueResource resource = newOverdueResource();
        final Response response = resource.getOverdueConfigJson(any());

        Assert.assertEquals(response.getStatus(), 200);
    }

    /**
     * Represent valid overdue configuration that can be marshalled into JSON.
     */
    @Test(groups = "fast")
    public void testGetOverdueConfigJson() throws Exception {
        final OverdueConfig overdueConfig = getOverdueConfig("overdue_valid.xml");

        when(context.createTenantContextNoAccountId(servletRequest)).thenReturn(tenantContext);
        when(overdueApi.getOverdueConfig(any())).thenReturn(overdueConfig);

        final OverdueResource resource = newOverdueResource();
        final Response response = resource.getOverdueConfigJson(any());

        Assert.assertEquals(response.getStatus(), 200);
    }


    /**
     * XML in test contains the same value as defined in: https://github.com/killbill/killbill/issues/1497, except that
     * 'timeSinceEarliestUnpaidInvoiceEqualsOrExceeds' element is added.
     */
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1497")
    public void testGetOverdueConfigJsonWithTimeSinceEarliest() throws Exception {
        final OverdueConfig overdueConfig = getOverdueConfig("overdue_withTimeSinceEarliest.xml");

        when(context.createTenantContextNoAccountId(servletRequest)).thenReturn(tenantContext);
        when(overdueApi.getOverdueConfig(any())).thenReturn(overdueConfig);

        final OverdueResource resource = newOverdueResource();
        final Response response = resource.getOverdueConfigJson(any());

        Assert.assertEquals(response.getStatus(), 200);
    }


    /**
     * XML in test have the same content as used in {@link #testGetOverdueConfigJson()}, but with
     * 'totalUnpaidInvoiceBalanceEqualsOrExceeds' removed in all condition. Purpose of this to prove that
     * 'totalUnpaidInvoiceBalanceEqualsOrExceeds' is not causing any problem when marshalling to JSON.
     */
    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1497")
    public void testGetOverdueConfigJsonWithTimeSinceEarliestNoTotalInvoice() throws Exception {
        final OverdueConfig overdueConfig = getOverdueConfig("overdue_withTimeSinceEarliestNoTotalUnpaidInvoice.xml");

        when(context.createTenantContextNoAccountId(servletRequest)).thenReturn(tenantContext);
        when(overdueApi.getOverdueConfig(any())).thenReturn(overdueConfig);

        final OverdueResource resource = newOverdueResource();
        final Response response = resource.getOverdueConfigJson(any());

        Assert.assertEquals(response.getStatus(), 200);
    }
}
