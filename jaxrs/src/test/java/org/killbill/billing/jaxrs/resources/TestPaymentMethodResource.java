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

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditUserApi;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class TestPaymentMethodResource extends JaxrsTestSuiteNoDB {

    private HttpServletRequest servletRequest;
    private AccountUserApi accountUserApi;
    private JaxrsUriBuilder uriBuilder;
    private PaymentApi paymentApi;
    private AuditUserApi auditUserApi;
    private Context context;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }
        servletRequest = mock(HttpServletRequest.class);
        accountUserApi = mock(AccountUserApi.class);
        uriBuilder = mock(JaxrsUriBuilder.class);
        paymentApi = mock(PaymentApi.class);
        auditUserApi = mock(AuditUserApi.class);
        context = mock(Context.class);
    }

    private PaymentMethodResource createPaymentMethodResource() {
        final PaymentMethodResource toSpy = new PaymentMethodResource(
                accountUserApi,
                uriBuilder,
                null,
                null,
                auditUserApi,
                paymentApi,
                null,
                clock,
                context
        );
        return Mockito.spy(toSpy);
    }

    @Test(groups = "fast")
    public void testGetPaymentMethod() throws AccountApiException, PaymentApiException {
        final PaymentMethod paymentMethod = mock(PaymentMethod.class);
        final Account account = mock(Account.class);
        final AuditMode fullMode = new AuditMode("FULL");

        when(paymentApi.getPaymentMethodById(any(), anyBoolean(), anyBoolean(), anyList(), any())).thenReturn(paymentMethod);
        when(accountUserApi.getAccountById(any(), any())).thenReturn(account);

        final PaymentMethodResource resource = createPaymentMethodResource();
        resource.getPaymentMethod(UUIDs.randomUUID(), false, false, Collections.emptyList(), fullMode, servletRequest);

        verify(accountUserApi, times(1)).getAccountById(any(), any());
        verify(auditUserApi, times(1)).getAccountAuditLogs(any(), eq(fullMode.getLevel()), any());
    }

    @Test(groups = "fast")
    public void testGetPaymentMethodByKey() throws AccountApiException, PaymentApiException {
        final PaymentMethod paymentMethod = mock(PaymentMethod.class);
        final Account account = mock(Account.class);
        final AuditMode fullMode = new AuditMode("FULL");

        when(paymentApi.getPaymentMethodByExternalKey(any(), anyBoolean(), anyBoolean(), anyList(), any())).thenReturn(paymentMethod);
        when(accountUserApi.getAccountById(any(), any())).thenReturn(account);

        final PaymentMethodResource resource = createPaymentMethodResource();
        resource.getPaymentMethodByKey("external-key", false, false, Collections.emptyList(), fullMode, servletRequest);

        verify(accountUserApi, times(1)).getAccountById(any(), any());
        verify(auditUserApi, times(1)).getAccountAuditLogs(any(), eq(fullMode.getLevel()), any());
    }
}
