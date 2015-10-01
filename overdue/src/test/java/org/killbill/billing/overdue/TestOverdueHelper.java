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

package org.killbill.billing.overdue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.overdue.api.OverdueState;
import org.mockito.Mockito;
import org.testng.Assert;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.overdue.glue.ApplicatorMockJunctionModule.ApplicatorBlockingApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class TestOverdueHelper {

    private final String configXml =
            "<overdueConfig>" +
            "   <accountOverdueStates>" +
            "       <initialReevaluationInterval>" +
            "           <unit>DAYS</unit><number>100</number>" +
            "       </initialReevaluationInterval>" +
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
            "   </accountOverdueStates>" +
            "</overdueConfig>";

    private final AccountInternalApi accountInternalApi;
    private final InvoiceInternalApi invoiceInternalApi;
    private final BlockingInternalApi blockingInternalApi;

    @Inject
    public TestOverdueHelper(final AccountInternalApi accountInternalApi,
                             final InvoiceInternalApi invoiceInternalApi,
                             final BlockingInternalApi blockingInternalApi) {
        this.accountInternalApi = accountInternalApi;
        this.invoiceInternalApi = invoiceInternalApi;
        this.blockingInternalApi = blockingInternalApi;
    }

    public void checkStateApplied(final OverdueState state) {
        final BlockingState result = ((ApplicatorBlockingApi) blockingInternalApi).getBlockingState();
        checkStateApplied(result, state);
    }

    public void checkStateApplied(final BlockingState result, final OverdueState state) {
        Assert.assertEquals(result.getStateName(), state.getName());
        Assert.assertEquals(result.isBlockChange(), state.isBlockChanges());
        Assert.assertEquals(result.isBlockEntitlement(), state.isDisableEntitlementAndChangesBlocked());
        Assert.assertEquals(result.isBlockBilling(), state.isDisableEntitlementAndChangesBlocked());
    }

    public ImmutableAccountData createImmutableAccountData(final LocalDate dateOfLastUnPaidInvoice) throws SubscriptionBaseApiException, AccountApiException {

        final UUID accountId = UUID.randomUUID();
        final ImmutableAccountData account = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountInternalApi.getImmutableAccountDataById(Mockito.eq(account.getId()), Mockito.<InternalTenantContext>any())).thenReturn(account);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(dateOfLastUnPaidInvoice);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(item);

        Mockito.when(invoice.getInvoiceItems()).thenReturn(items);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice);
        Mockito.when(invoiceInternalApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<LocalDate>any(), Mockito.<InternalTenantContext>any())).thenReturn(invoices);

        return account;
    }

    public String getConfigXml() {
        return configXml;
    }
}
