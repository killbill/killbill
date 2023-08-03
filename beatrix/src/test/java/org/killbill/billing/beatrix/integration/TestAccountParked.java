/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.TagDefinition;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.commons.utils.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestAccountParked  extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2023-07-27T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);

        testCallContext = setupCallContextWithTenantAndAccount(testCallContext.getTenantId(), account.getId());


    }

    @Test(groups = "slow")
    public void testAccountParked() throws Exception {

        uploadCatalog("catalog-v1.xml");
        assertListenerStatus();

        // Create base Subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("standard-monthly")), "externalKey", null, null, false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, testCallContext);

        //add addons
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("standard-ao-monthly")), null, null, false, Collections.emptyList(), testCallContext);
        entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("standard-ao2-monthly")), null, null, false, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        //move clock by a month and verify that invoice is generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2023-08-27T12:56:02"));
        assertListenerStatus();

        Invoice invoice = invoiceUserApi.getInvoicesByAccount(account.getId(), true, true, true, testCallContext).get(0);
        assertNotNull(invoice);
        assertEquals(invoice.getInvoiceItems().size(), 3);

        Payment payment = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), testCallContext).get(0);
        assertNotNull(payment);

        //refund payment with invoice item adjustment
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        Map<UUID, BigDecimal> adjustments = Map.of(invoice.getInvoiceItems().get(1).getId(), new BigDecimal(10));
        invoicePaymentApi.createRefundForInvoicePayment(true, adjustments, account, payment.getId(), payment.getPurchasedAmount(), payment.getCurrency(), null, null, Collections.emptyList(), PAYMENT_OPTIONS, testCallContext);
        assertListenerStatus();

        //upload catalog with price change
        uploadCatalog("catalog-v2.xml");
        assertListenerStatus();

        //move clock by a month - nothing happens
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2023-09-27T12:56:02"));
        assertListenerStatus();

        //move clock by a month - account is PARKED - IllegalStateException is seen in the logs at this point - IllegalStateException: Double billing detected: [Item{id=273b64de-3188-4179-ae8c-803fc6345c1c, accountId=b87ce905-0c21-43dc-b13c-a0b2cbb0d0fb, bundleId=6b1dea82-d691-45a6-aa8f-83a658e799a2, subscriptionId=a7c7be18-8409-4183-99f7-9838d32f4ae3, targetInvoiceId=9aa50087-866c-4bf6-ab8c-ac8734c3d769, invoiceId=75058efd-b8c5-4116-888b-4cfa2eff15e5, productName='standard-ao2', planName='standard-ao2-monthly', phaseName='standard-ao2-monthly-evergreen', startDate=2023-07-27, endDate=2023-08-27, amount=15.00, rate=15.000000000, currency=USD, createdDate=2023-08-27T12:56:02.000Z, linkedId=null, currentRepairedAmount=0, adjustedAmount=10.00, action=ADD}, Item{id=f89dc625-5bf0-4315-b3c1-a0c1ca9c5858, accountId=b87ce905-0c21-43dc-b13c-a0b2cbb0d0fb, bundleId=6b1dea82-d691-45a6-aa8f-83a658e799a2, subscriptionId=a7c7be18-8409-4183-99f7-9838d32f4ae3, targetInvoiceId=9aa50087-866c-4bf6-ab8c-ac8734c3d769, invoiceId=f9849e90-46e2-488e-82bb-1d70c6baece6, productName='standard-ao2', planName='standard-ao2-monthly', phaseName='standard-ao2-monthly-evergreen', startDate=2023-07-27, endDate=2023-08-27, amount=18.00, rate=18.000000000, currency=USD, createdDate=2023-09-27T12:56:02.000Z, linkedId=null, currentRepairedAmount=0, adjustedAmount=0, action=ADD}, Item{id=c543e3e5-d230-4420-89de-ce4c1f6306bb, accountId=b87ce905-0c21-43dc-b13c-a0b2cbb0d0fb, bundleId=null, subscriptionId=null, targetInvoiceId=9aa50087-866c-4bf6-ab8c-ac8734c3d769, invoiceId=f9849e90-46e2-488e-82bb-1d70c6baece6, productName='null', planName='null', phaseName='null', startDate=2023-07-27, endDate=2023-08-27, amount=5.00, rate=null, currency=USD, createdDate=2023-09-27T12:56:02.000Z, linkedId=273b64de-3188-4179-ae8c-803fc6345c1c, currentRepairedAmount=0, adjustedAmount=0, action=CANCEL}]
        busHandler.pushExpectedEvents(NextEvent.TAG);
        clock.setTime(new DateTime("2023-10-27T12:56:02"));
        assertListenerStatus();

        final Tag tag = tagUserApi.getTagsForAccount(account.getId(), false, testCallContext).get(0);
        assertNotNull(tag);
        assertEquals(tag.getTagDefinitionId(), SystemTags.PARK_TAG_DEFINITION_ID);
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(Resources.getResource("catalogs/testAccountsParked/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }
}
