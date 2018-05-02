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

package org.killbill.billing.beatrix.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;

import com.google.inject.Inject;

import static org.testng.Assert.assertNotNull;

public class TestCustomFieldApi extends TestIntegrationBase {

    private Account account;

    @Inject
    private CustomFieldUserApi customFieldApi;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
    }

    @Test(groups = "slow")
    public void testCustomFieldForAccount() throws CustomFieldApiException {
        addCustomField("name1", "value1", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());
        addCustomField("name2", "value2", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());

        List<CustomField> fields = customFieldApi.getCustomFieldsForAccount(account.getId(), callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForAccountType(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(fields.size(), 2);
    }

    @Test(groups = "slow")
    public void testCustomFieldForInvoice() throws CustomFieldApiException, SubscriptionBaseApiException {

        //
        // Create necessary logic to end up with an Invoice object on that account.
        //
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        Assert.assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        Assert.assertEquals(invoice.getAccountId(), account.getId());

        addCustomField("name1", "value1", invoice.getId(), ObjectType.INVOICE, clock.getUTCNow());
        addCustomField("name2", "value2", invoice.getId(), ObjectType.INVOICE, clock.getUTCNow());

        List<CustomField> fields = customFieldApi.getCustomFieldsForAccount(invoice.getId(), callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForAccountType(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        //
        // Add custom field on account and retry
        //
        addCustomField("foo", "bar", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());

        fields = customFieldApi.getCustomFieldsForAccount(invoice.getId(), callContext);
        Assert.assertEquals(fields.size(), 3);

        fields = customFieldApi.getCustomFieldsForAccountType(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);
    }

    private void addCustomField(String name, String value, UUID objectId, ObjectType type, DateTime createdDate) throws CustomFieldApiException {
        CustomField f = new StringCustomField(name, value, type, objectId, clock.getUTCNow());
        busHandler.pushExpectedEvents(NextEvent.CUSTOM_FIELD);
        List<CustomField> fields = new ArrayList<CustomField>();
        fields.add(f);
        customFieldApi.addCustomFields(fields, callContext);
        assertListenerStatus();
    }
}
