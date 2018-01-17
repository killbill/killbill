/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.GuicyKillbillTestSuite;
import org.killbill.billing.ObjectType;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.AuditLog;
import org.killbill.billing.client.model.CustomField;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Tag;
import org.killbill.billing.client.model.TagDefinition;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public class TestInvoiceItem extends TestJaxrsBase {

    @Test(groups = "slow", description = "Add tags to invoice item")
    public void testTags() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final Invoices invoicesJson = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);

        Assert.assertNotNull(invoicesJson);
        Assert.assertEquals(invoicesJson.size(), 2);

        final List<InvoiceItem> invoiceItems = invoicesJson.get(0).getItems();

        Assert.assertNotNull(invoiceItems);

        // Create tag definition
        final TagDefinition input = new TagDefinition(null, false, "tagtest", "invoice item tag test", ImmutableList.<ObjectType>of(ObjectType.INVOICE_ITEM));

        final TagDefinition objFromJson = killBillClient.createTagDefinition(input, requestOptions);
        Assert.assertNotNull(objFromJson);
        Assert.assertEquals(objFromJson.getName(), input.getName());
        Assert.assertEquals(objFromJson.getDescription(), input.getDescription());

        // Add a tag
        final Multimap<String, String> followQueryParams = HashMultimap.create();
        followQueryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId().toString());
        final RequestOptions followRequestOptions = requestOptions.extend().withQueryParamsForFollow(followQueryParams).build();
        killBillClient.createInvoiceItemTag(invoiceItems.get(0).getInvoiceItemId(),objFromJson.getId(), followRequestOptions);

        // Add account id to request
        final Multimap<String, String> queryParams = HashMultimap.create();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId().toString());
        final RequestOptions addedRequestOptions = requestOptions.extend().withQueryParams(queryParams).build();
        
        // Retrieves all tags
        final List<Tag> tags1 = killBillClient.getInvoiceItemTags(invoiceItems.get(0).getInvoiceItemId(), AuditLevel.FULL, addedRequestOptions);
        Assert.assertEquals(tags1.size(), 1);
        Assert.assertEquals(tags1.get(0).getTagDefinitionId(), objFromJson.getId());

        // Verify adding the same tag a second time doesn't do anything
        killBillClient.createInvoiceItemTag(invoiceItems.get(0).getInvoiceItemId(), objFromJson.getId(), followRequestOptions);

        // Retrieves all tags again
        final List<Tag> tags2 = killBillClient.getInvoiceItemTags(invoiceItems.get(0).getInvoiceItemId(), AuditLevel.FULL, addedRequestOptions);
        Assert.assertEquals(tags2, tags1);

        // Verify audit logs
        Assert.assertEquals(tags2.get(0).getAuditLogs().size(), 1);
        final AuditLog auditLogJson = tags2.get(0).getAuditLogs().get(0);
        Assert.assertEquals(auditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(auditLogJson.getChangedBy(), createdBy);
        Assert.assertEquals(auditLogJson.getReasonCode(), reason);
        Assert.assertEquals(auditLogJson.getComments(), comment);
        Assert.assertNotNull(auditLogJson.getChangeDate());
        Assert.assertNotNull(auditLogJson.getUserToken());

        // remove it
        killBillClient.deleteInvoiceItemTag(invoiceItems.get(0).getInvoiceItemId(), objFromJson.getId(), requestOptions);
        final List<Tag> tags3 = killBillClient.getInvoiceItemTags(invoiceItems.get(0).getInvoiceItemId(), AuditLevel.FULL, addedRequestOptions);
        Assert.assertEquals(tags3.size(), 0);

        killBillClient.deleteTagDefinition(objFromJson.getId(),requestOptions);
        List<TagDefinition> objsFromJson = killBillClient.getTagDefinitions(requestOptions);
        Assert.assertNotNull(objsFromJson);
        Boolean isFound = false;
        for (TagDefinition tagDefinition : objsFromJson){
            isFound |= tagDefinition.getId().equals(objFromJson.getId());
        }
        Assert.assertFalse(isFound);
    }

    @Test(groups = "slow", description = "Add custom fields to invoice item")
    public void testCustomFields() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final Invoices invoicesJson = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);

        Assert.assertNotNull(invoicesJson);
        Assert.assertEquals(invoicesJson.size(), 2);

        final List<InvoiceItem> invoiceItems = invoicesJson.get(0).getItems();

        Assert.assertNotNull(invoiceItems);

        final Collection<CustomField> customFields = new LinkedList<CustomField>();
        customFields.add(new CustomField(null, invoiceItems.get(0).getInvoiceItemId(), ObjectType.INVOICE_ITEM, "1", "value1", null));
        customFields.add(new CustomField(null, invoiceItems.get(0).getInvoiceItemId(), ObjectType.INVOICE_ITEM, "2", "value2", null));
        customFields.add(new CustomField(null, invoiceItems.get(0).getInvoiceItemId(), ObjectType.INVOICE_ITEM, "3", "value3", null));

        killBillClient.createInvoiceItemCustomField(invoiceItems.get(0).getInvoiceItemId(), customFields, requestOptions);

        final List<CustomField> invoiceItemCustomFields = killBillClient.getInvoiceItemCustomFields(invoiceItems.get(0).getInvoiceItemId(), requestOptions);
        Assert.assertEquals(invoiceItemCustomFields.size(), 3);

        // Delete all custom fields for account
        killBillClient.deleteInvoiceItemCustomFields(invoiceItems.get(0).getInvoiceItemId(), requestOptions);

        final List<CustomField> remainingCustomFields = killBillClient.getInvoiceItemCustomFields(invoiceItems.get(0).getInvoiceItemId(), requestOptions);
        Assert.assertEquals(remainingCustomFields.size(), 0);
    }
}
