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

package com.ning.billing.overdue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.overdue.glue.ApplicatorMockJunctionModule.ApplicatorBlockingApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class TestOverdueHelper {

    private final String configXml =
            "<overdueConfig>" +
            "   <bundleOverdueStates>" +
            "       <state name=\"OD3\">" +
            "           <condition>" +
            "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "                   <unit>DAYS</unit><number>50</number>" +
            "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "           </condition>" +
            "           <externalMessage>Reached OD3</externalMessage>" +
            "           <blockChanges>true</blockChanges>" +
            "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
            "           <autoReevaluationInterval>" +
            "               <unit>DAYS</unit><number>5</number>" +
            "           </autoReevaluationInterval>" +
            "       </state>" +
            "       <state name=\"OD2\">" +
            "           <condition>" +
            "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "                   <unit>DAYS</unit><number>40</number>" +
            "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "           </condition>" +
            "           <externalMessage>Reached OD2</externalMessage>" +
            "           <blockChanges>true</blockChanges>" +
            "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
            "           <autoReevaluationInterval>" +
            "               <unit>DAYS</unit><number>5</number>" +
            "           </autoReevaluationInterval>" +
            "       </state>" +
            "       <state name=\"OD1\">" +
            "           <condition>" +
            "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "                   <unit>DAYS</unit><number>30</number>" +
            "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
            "           </condition>" +
            "           <externalMessage>Reached OD1</externalMessage>" +
            "           <blockChanges>true</blockChanges>" +
            "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
            "           <autoReevaluationInterval>" +
            "               <unit>DAYS</unit><number>100</number>" + // this number is intentionally too high
            "           </autoReevaluationInterval>" +
            "       </state>" +
            "   </bundleOverdueStates>" +
            "</overdueConfig>";

    private final AccountInternalApi accountInternalApi;
    private final SubscriptionInternalApi subscriptionInternalApi;
    private final InvoiceInternalApi invoiceInternalApi;
    private final BlockingInternalApi blockingInternalApi;

    @Inject
    public TestOverdueHelper(final AccountInternalApi accountInternalApi, final SubscriptionInternalApi subscriptionInternalApi,
                             final InvoiceInternalApi invoiceInternalApi, final BlockingInternalApi blockingInternalApi) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.invoiceInternalApi = invoiceInternalApi;
        this.blockingInternalApi = blockingInternalApi;
    }

    public void checkStateApplied(final OverdueState<SubscriptionBundle> state) {
        final BlockingState result = ((ApplicatorBlockingApi) blockingInternalApi).getBlockingState();
        checkStateApplied(result, state);
    }

    public void checkStateApplied(final BlockingState result, final OverdueState<SubscriptionBundle> state) {
        Assert.assertEquals(result.getStateName(), state.getName());
        Assert.assertEquals(result.isBlockChange(), state.blockChanges());
        Assert.assertEquals(result.isBlockEntitlement(), state.disableEntitlementAndChangesBlocked());
        Assert.assertEquals(result.isBlockBilling(), state.disableEntitlementAndChangesBlocked());
    }

    public SubscriptionBundle createBundle(final LocalDate dateOfLastUnPaidInvoice) throws SubscriptionUserApiException, AccountApiException {
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        final UUID bundleId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);

        final UUID accountId = UUID.randomUUID();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(account.getId()), Mockito.<InternalTenantContext>any())).thenReturn(account);

        Mockito.when(bundle.getAccountId()).thenReturn(accountId);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(dateOfLastUnPaidInvoice);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getBundleId()).thenReturn(bundleId);
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(item);

        Mockito.when(invoice.getInvoiceItems()).thenReturn(items);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice);
        Mockito.when(invoiceInternalApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<LocalDate>any(), Mockito.<InternalTenantContext>any())).thenReturn(invoices);

        final SubscriptionBase base = Mockito.mock(SubscriptionBase.class);
        Mockito.when(base.getCurrentPlan()).thenReturn(MockPlan.createBicycleNoTrialEvergreen1USD());
        Mockito.when(base.getCurrentPriceList()).thenReturn(new MockPriceList());
        Mockito.when(base.getCurrentPhase()).thenReturn(MockPlan.createBicycleNoTrialEvergreen1USD().getFinalPhase());
        Mockito.when(subscriptionInternalApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(base);

        return bundle;
    }

    public String getConfigXml() {
        return configXml;
    }
}
