/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.jaxrs;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.AuditLogJson;
import com.ning.billing.jaxrs.json.BillCycleDayJson;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonWithBundleKeys;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.json.TagJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.util.ChangeType;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestAccount extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testAccountOk() throws Exception {
        final AccountJson input = createAccount();

        // Retrieves by external key
        final AccountJson retrievedAccount = getAccountByExternalKey(input.getExternalKey());
        Assert.assertTrue(retrievedAccount.equals(input));

        // Update Account
        final AccountJson newInput = new AccountJson(input.getAccountId(),
                                                     "zozo", 4, input.getExternalKey(), "rr@google.com", new BillCycleDayJson(18, 18),
                                                     "USD", null, "UTC", "bl1", "bh2", "", "", "ca", "San Francisco", "usa", "en", "415-255-2991",
                                                     false, false);
        final AccountJson updatedAccount = updateAccount(input.getAccountId(), newInput);
        Assert.assertTrue(updatedAccount.equals(newInput));
    }

    @Test(groups = "slow")
    public void testUpdateNonExistentAccount() throws Exception {
        final AccountJson input = getAccountJson();

        final String baseJson = mapper.writeValueAsString(input);
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + input.getAccountId();
        final Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountNonExistent() throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountBadAccountId() throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/yo";
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountTimeline() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final AccountTimelineJson timeline = getAccountTimeline(accountJson.getAccountId());
        Assert.assertEquals(timeline.getPayments().size(), 1);
        Assert.assertEquals(timeline.getInvoices().size(), 2);
        Assert.assertEquals(timeline.getBundles().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 2);
    }

    @Test
    public void testAccountTimelineWithAudits() throws Exception {
        final DateTime startTime = clock.getUTCNow();
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final DateTime endTime = clock.getUTCNow();

        // Add credit
        final InvoiceJsonSimple invoice = getInvoicesForAccount(accountJson.getAccountId()).get(1);
        final DateTime creditEffectiveDate = clock.getUTCNow();
        final BigDecimal creditAmount = BigDecimal.ONE;
        createCreditForInvoice(accountJson.getAccountId(), invoice.getInvoiceId(),
                               creditAmount, clock.getUTCNow(), creditEffectiveDate);

        // Add refund
        final PaymentJsonSimple postedPayment = getPaymentsForAccount(accountJson.getAccountId()).get(0);
        final BigDecimal refundAmount = BigDecimal.ONE;
        createRefund(postedPayment.getPaymentId(), refundAmount);

        // Add chargeback
        final BigDecimal chargebackAmount = BigDecimal.ONE;
        createChargeBack(postedPayment.getPaymentId(), chargebackAmount);

        final AccountTimelineJson timeline = getAccountTimelineWithAudits(accountJson.getAccountId());

        // Verify payments
        Assert.assertEquals(timeline.getPayments().size(), 1);
        final PaymentJsonWithBundleKeys paymentJson = timeline.getPayments().get(0);
        final List<AuditLogJson> paymentAuditLogs = paymentJson.getAuditLogs();
        Assert.assertEquals(paymentAuditLogs.size(), 2);
        verifyAuditLog(paymentAuditLogs.get(0), ChangeType.INSERT, null, null, "PaymentRequestProcessor", startTime, endTime);
        verifyAuditLog(paymentAuditLogs.get(1), ChangeType.UPDATE, null, null, "PaymentRequestProcessor", startTime, endTime);

        // Verify refunds
        Assert.assertEquals(paymentJson.getRefunds().size(), 1);
        final RefundJson refundJson = paymentJson.getRefunds().get(0);
        Assert.assertEquals(refundJson.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(refundJson.getRefundAmount().compareTo(refundAmount), 0);
        final List<AuditLogJson> refundAuditLogs = refundJson.getAuditLogs();
        Assert.assertEquals(refundAuditLogs.size(), 3);
        verifyAuditLog(refundAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
        verifyAuditLog(refundAuditLogs.get(1), ChangeType.UPDATE, reason, comment, createdBy, startTime, endTime);
        verifyAuditLog(refundAuditLogs.get(2), ChangeType.UPDATE, reason, comment, createdBy, startTime, endTime);

        // Verify chargebacks
        Assert.assertEquals(paymentJson.getChargebacks().size(), 1);
        final ChargebackJson chargebackJson = paymentJson.getChargebacks().get(0);
        Assert.assertEquals(chargebackJson.getPaymentId(), paymentJson.getPaymentId());
        Assert.assertEquals(chargebackJson.getChargebackAmount().compareTo(chargebackAmount), 0);
        final List<AuditLogJson> chargebackAuditLogs = chargebackJson.getAuditLogs();
        Assert.assertEquals(chargebackAuditLogs.size(), 1);
        verifyAuditLog(chargebackAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);

        // Verify invoices
        Assert.assertEquals(timeline.getInvoices().size(), 2);
        final List<AuditLogJson> firstInvoiceAuditLogs = timeline.getInvoices().get(0).getAuditLogs();
        Assert.assertEquals(firstInvoiceAuditLogs.size(), 1);
        verifyAuditLog(firstInvoiceAuditLogs.get(0), ChangeType.INSERT, null, null, "Transition", startTime, endTime);
        final List<AuditLogJson> secondInvoiceAuditLogs = timeline.getInvoices().get(1).getAuditLogs();
        Assert.assertEquals(secondInvoiceAuditLogs.size(), 1);
        verifyAuditLog(secondInvoiceAuditLogs.get(0), ChangeType.INSERT, null, null, "Transition", startTime, endTime);

        // Verify credits
        final List<CreditJson> credits = timeline.getInvoices().get(1).getCredits();
        Assert.assertEquals(credits.size(), 1);
        Assert.assertEquals(credits.get(0).getCreditAmount().compareTo(creditAmount.negate()), 0);
        final List<AuditLogJson> creditAuditLogs = credits.get(0).getAuditLogs();
        Assert.assertEquals(creditAuditLogs.size(), 1);
        verifyAuditLog(creditAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);

        // Verify bundles
        Assert.assertEquals(timeline.getBundles().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 2);
        final List<AuditLogJson> bundleAuditLogs = timeline.getBundles().get(0).getAuditLogs();
        Assert.assertEquals(bundleAuditLogs.size(), 3);
        verifyAuditLog(bundleAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
        verifyAuditLog(bundleAuditLogs.get(1), ChangeType.UPDATE, null, null, "Transition", startTime, endTime);
        verifyAuditLog(bundleAuditLogs.get(2), ChangeType.UPDATE, null, null, "Transition", startTime, endTime);

        // TODO subscription events audit logs
    }

    private void verifyAuditLog(final AuditLogJson auditLogJson, final ChangeType changeType, @Nullable final String reasonCode,
                                @Nullable final String comments, @Nullable final String changedBy,
                                final DateTime startTime, final DateTime endTime) {
        Assert.assertEquals(auditLogJson.getChangeType(), changeType.toString());
        Assert.assertFalse(auditLogJson.getChangeDate().isBefore(startTime));
        // Flaky
        //Assert.assertFalse(auditLogJson.getChangeDate().isAfter(endTime));
        Assert.assertEquals(auditLogJson.getReasonCode(), reasonCode);
        Assert.assertEquals(auditLogJson.getComments(), comments);
        Assert.assertEquals(auditLogJson.getChangedBy(), changedBy);
    }

    @Test(groups = "slow")
    public void testAccountPaymentMethods() throws Exception {

        final AccountJson accountJson = createAccount("qwerty", "ytrewq", "qwerty@yahoo.com");
        assertNotNull(accountJson);

        String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        PaymentMethodJson paymentMethodJson = getPaymentMethodJson(accountJson.getAccountId(), getPaymentMethodCCProperties());
        String baseJson = mapper.writeValueAsString(paymentMethodJson);
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_IS_DEFAULT, "true");

        Response response = doPost(uri, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationCC = response.getHeader("Location");
        Assert.assertNotNull(locationCC);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(locationCC, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodCC = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertTrue(paymentMethodCC.isDefault());
        //
        // Add another payment method
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        paymentMethodJson = getPaymentMethodJson(accountJson.getAccountId(), getPaymentMethodPaypalProperties());
        baseJson = mapper.writeValueAsString(paymentMethodJson);

        response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationPP = response.getHeader("Location");
        assertNotNull(locationPP);
        response = doGetWithUrl(locationPP, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodPP = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertFalse(paymentMethodPP.isDefault());

        //
        // FETCH ALL PAYMENT METHODS
        //
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        List<PaymentMethodJson> paymentMethods = mapper.readValue(baseJson, new TypeReference<List<PaymentMethodJson>>() {});
        assertEquals(paymentMethods.size(), 2);

        //
        // CHANGE DEFAULT
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS + "/" + paymentMethodPP.getPaymentMethodId() + "/" + JaxrsResource.PAYMENT_METHODS_DEFAULT_PATH_POSTFIX;
        response = doPut(uri, "{}", DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        response = doGetWithUrl(locationPP, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodPPDefault = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertTrue(paymentMethodPPDefault.isDefault());

        //
        // DELETE NON DEFAULT PM
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodCC.getPaymentMethodId();
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        //
        // FETCH ALL PAYMENT METHODS
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        paymentMethods = mapper.readValue(baseJson, new TypeReference<List<PaymentMethodJson>>() {});
        assertEquals(paymentMethods.size(), 1);

        //
        // DELETE DEFAULT PAYMENT METHOD (without special flag first)
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodPP.getPaymentMethodId();
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());

        //
        // RETRY TO DELETE DEFAULT PAYMENT METHOD (with special flag this time)
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodPP.getPaymentMethodId();
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF, "true");

        response = doDelete(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        // CHECK ACCOUNT IS NOW AUTO_PAY_OFF

        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.TAGS;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        List<TagJson> tagsJson = mapper.readValue(baseJson, new TypeReference<List<TagJson>>() {});
        Assert.assertEquals(tagsJson.size(), 1);
        TagJson tagJson = tagsJson.get(0);
        Assert.assertEquals(tagJson.getTagDefinitionName(), "AUTO_PAY_OFF");
        Assert.assertEquals(tagJson.getTagDefinitionId(), new UUID(0, 1).toString());

        // FETCH ACCOUNT AGAIN AND CHECK THERE IS NO DEFAULT PAYMENT METHOD SET
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        AccountJson updatedAccountJson = mapper.readValue(response.getResponseBody(), AccountJson.class);
        Assert.assertEquals(updatedAccountJson.getAccountId(), accountJson.getAccountId());
        Assert.assertNull(updatedAccountJson.getPaymentMethodId());

        //
        // FINALLY TRY TO REMOVE AUTO_PAY_OFF WITH NO DEFAULT PAYMENT METHOD ON ACCOUNT
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.TAGS;
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_TAGS, new UUID(0, 1).toString());
        response = doDelete(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());

    }

    @Test(groups = "slow")
    public void testAccountPaymentsWithRefund() throws Exception {
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify payments
        final List<PaymentJsonSimple> objFromJson = getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(objFromJson.size(), 1);

        // Verify refunds
        final List<RefundJson> objRefundFromJson = getRefundsForAccount(accountJson.getAccountId());
        Assert.assertEquals(objRefundFromJson.size(), 0);
    }

    @Test(groups = "slow")
    public void testTags() throws Exception {

        // Use tag definition for AUTO_PAY_OFF
        final TagDefinitionJson input = new TagDefinitionJson(new UUID(0, 1).toString(), "AUTO_PAY_OFF", "nothing more to say");

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_TAGS, input.getId());
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + UUID.randomUUID().toString() + "/" + JaxrsResource.TAGS;
        Response response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        // Retrieves by Id based on Location returned
        final String url = getUrlFromUri(uri);
        response = doGetWithUrl(url, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test(groups = "slow")
    public void testCustomFields() throws Exception {

        final AccountJson accountJson = createAccount("yoyoq", "gfgrqe", "yoyoq@yahoo.com");
        assertNotNull(accountJson);

        final List<CustomFieldJson> customFields = new LinkedList<CustomFieldJson>();
        customFields.add(new CustomFieldJson("1", "value1"));
        customFields.add(new CustomFieldJson("2", "value2"));
        customFields.add(new CustomFieldJson("3", "value3"));
        final String baseJson = mapper.writeValueAsString(customFields);

        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.CUSTOM_FIELDS;
        Response response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        // Retrieves by Id based on Location returned
        final String url = getUrlFromUri(uri);
        response = doGetWithUrl(url, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }
}
