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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.JaxrsResource;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.jaxrs.json.AdminPaymentJson;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestAdmin extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testAdminPaymentEndpoint() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        final String paymentExternalKey = "extkey";

        // Create Authorization
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.TEN);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(paymentExternalKey);
        authTransaction.setTransactionExternalKey(authTransactionExternalKey);
        authTransaction.setTransactionType("AUTHORIZE");
        callbackServlet.pushExpectedEvent(ExtBusEventType.PAYMENT_SUCCESS);
        final Payment authPayment = killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, requestOptions);
        callbackServlet.assertListenerStatus();

        fixPaymentState(authPayment, "AUTH_FAILED", "AUTH_FAILED", TransactionStatus.PAYMENT_FAILURE);

        final Payment updatedPayment = killBillClient.getPayment(authPayment.getPaymentId());
        Assert.assertEquals(updatedPayment.getTransactions().size(), 1);
        final PaymentTransaction authTransaction2 = updatedPayment.getTransactions().get(0);
        Assert.assertEquals(authTransaction2.getStatus(), TransactionStatus.PAYMENT_FAILURE.toString());

        // Capture should fail because lastSuccessPaymentState was moved to AUTH_FAILED
        doCapture(updatedPayment, true);
    }

    @Test(groups = "slow")
    public void testAdminInvoiceEndpoint() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final List<UUID> accounts = new LinkedList<UUID>();
        for (int i = 0; i < 5; i++) {
            final Account accountJson = createAccountWithDefaultPaymentMethod();
            assertNotNull(accountJson);
            accounts.add(accountJson.getAccountId());

            createEntitlement(accountJson.getAccountId(),
                              UUID.randomUUID().toString(),
                              "Shotgun",
                              ProductCategory.BASE,
                              BillingPeriod.MONTHLY,
                              true);
            clock.addDays(2);

            Assert.assertEquals(killBillClient.getInvoices(requestOptions).getPaginationMaxNbRecords(), i + 1);
            final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), false, false, false, false, AuditLevel.NONE, requestOptions);
            assertEquals(invoices.size(), 1);
        }

        // Trigger first non-trial invoice
        for (int i = 0; i < 5; i++) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_SUCCESS, ExtBusEventType.PAYMENT_SUCCESS);
        }
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        Assert.assertEquals(killBillClient.getInvoices(requestOptions).getPaginationMaxNbRecords(), 10);
        for (final UUID accountId : accounts) {
            final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountId, false, false, false, false, AuditLevel.NONE, requestOptions);
            assertEquals(invoices.size(), 2);
        }

        // Upload the config
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> perTenantProperties = new HashMap<String, String>();
        perTenantProperties.put("org.killbill.invoice.enabled", "false");
        final String perTenantConfig = mapper.writeValueAsString(perTenantProperties);
        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        killBillClient.postConfigurationPropertiesForTenant(perTenantConfig, requestOptions);
        callbackServlet.assertListenerStatus();

        // Verify the second invoice isn't generated
        for (int i = 0; i < 5; i++) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION);
        }
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        Assert.assertEquals(killBillClient.getInvoices(requestOptions).getPaginationMaxNbRecords(), 10);
        for (final UUID accountId : accounts) {
            final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountId, false, false, false, false, AuditLevel.NONE, requestOptions);
            assertEquals(invoices.size(), 2);
        }

        // Fix one account
        final Response response = triggerInvoiceGenerationForParkedAccounts(1);
        Assert.assertEquals(response.getResponseBody(), "{\"" + accounts.get(0) + "\":\"OK\"}");
        Assert.assertEquals(killBillClient.getInvoices(requestOptions).getPaginationMaxNbRecords(), 11);

        // Fix all accounts
        final Response response2 = triggerInvoiceGenerationForParkedAccounts(4);
        final Map<String,String> fixedAccounts = mapper.readValue(response2.getResponseBody(), new TypeReference<Map<String,String>>() {});
        Assert.assertEquals(fixedAccounts.size(), 4);
        Assert.assertEquals(fixedAccounts.get(accounts.get(1).toString()), "OK");
        Assert.assertEquals(fixedAccounts.get(accounts.get(2).toString()), "OK");
        Assert.assertEquals(fixedAccounts.get(accounts.get(3).toString()), "OK");
        Assert.assertEquals(fixedAccounts.get(accounts.get(4).toString()), "OK");
        Assert.assertEquals(killBillClient.getInvoices(requestOptions).getPaginationMaxNbRecords(), 15);
    }

    private void doCapture(final Payment payment, final boolean expectException) throws KillBillClientException {
        // Payment object does not export state, this is purely internal, so to verify that we indeed changed to Failed, we can attempt
        // a capture, which should fail
        final String capture1TransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(payment.getPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(payment.getCurrency());
        captureTransaction.setPaymentExternalKey(payment.getPaymentExternalKey());
        captureTransaction.setTransactionExternalKey(capture1TransactionExternalKey);
        try {
            killBillClient.captureAuthorization(captureTransaction, requestOptions);
            if (expectException) {
                Assert.fail("Capture should not succeed, after auth was moved to a PAYMENT_FAILURE");
            }
        } catch (final KillBillClientException mabeExpected) {
            if (!expectException) {
                throw mabeExpected;
            }
        }

    }

    private void fixPaymentState(final Payment payment, final String lastSuccessPaymentState, final String currentPaymentStateName, final TransactionStatus transactionStatus) throws KillBillClientException {
        //
        // We do not expose the endpoint in the client API on purpose since this should only be accessed using special permission ADMIN_CAN_FIX_DATA
        // for when there is a need to fix payment state.
        //
        final String uri = "/1.0/kb/admin/payments/" + payment.getPaymentId().toString() + "/transactions/" + payment.getTransactions().get(0).getTransactionId().toString();

        final AdminPaymentJson body = new AdminPaymentJson(lastSuccessPaymentState, currentPaymentStateName, transactionStatus.toString());
        final Multimap result = HashMultimap.create();
        result.put(KillBillHttpClient.AUDIT_OPTION_CREATED_BY, createdBy);
        result.put(KillBillHttpClient.AUDIT_OPTION_REASON, reason);
        result.put(KillBillHttpClient.AUDIT_OPTION_COMMENT, comment);
        callbackServlet.pushExpectedEvent(ExtBusEventType.PAYMENT_FAILED);
        killBillHttpClient.doPut(uri, body, result);
        callbackServlet.assertListenerStatus();
    }

    private Response triggerInvoiceGenerationForParkedAccounts(final int limit) throws KillBillClientException {
        final String uri = "/1.0/kb/admin/invoices";

        final RequestOptions requestOptions = RequestOptions.builder()
                                                            .withQueryParams(ImmutableMultimap.<String, String>of(JaxrsResource.QUERY_SEARCH_LIMIT, String.valueOf(limit)))
                                                            .withCreatedBy(createdBy)
                                                            .withReason(reason)
                                                            .withComment(comment).build();
        for (int i = 0; i < limit; i++) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_DELETION, ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_SUCCESS, ExtBusEventType.PAYMENT_SUCCESS);
        }
        final Response response = killBillHttpClient.doPost(uri, null, requestOptions);
        callbackServlet.assertListenerStatus();
        return response;
    }
}
