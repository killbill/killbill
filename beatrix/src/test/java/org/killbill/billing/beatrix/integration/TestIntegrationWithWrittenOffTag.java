/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithWrittenOffTag extends TestIntegrationBase {

    @Inject
    private InvoiceUserApi invoiceApi;

    @Inject
    private TagUserApi tagApi;

    private Account account;
    private String productName;
    private BillingPeriod term;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
    }

    @Test(groups = "slow")
    public void testWithWrittenOffInvoice() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));


        // Put the account in AUTO_PAY_OFF to make sure payment system does not try to pay initial invoices
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(31);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        // Tag non $0 invoice with WRITTEN_OFF and remove AUTO_PAY_OFF => System should still not pay anything
        add_WRITTEN_OFF_Tag(invoices.get(1).getId(), ObjectType.INVOICE);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();

        List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(accountPayments.size(), 0);

    }

    private void add_WRITTEN_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagApi.addTag(id, type, ControlTagType.WRITTEN_OFF.getId(), callContext);
        assertListenerStatus();
        final List<Tag> tags = tagApi.getTagsForObject(id, type, false, callContext);
        assertEquals(tags.size(), 1);
    }

}
