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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionEvent;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;
import com.ning.billing.util.audit.AuditLog;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestBundleTimelineJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String viewId = UUID.randomUUID().toString();
        final String reason = UUID.randomUUID().toString();

        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = createBundleWithSubscriptions();
        final InvoiceJsonSimple invoiceJsonSimple = createInvoice();
        final PaymentJsonSimple paymentJsonSimple = createPayment(UUID.fromString(invoiceJsonSimple.getAccountId()),
                                                                  UUID.fromString(invoiceJsonSimple.getInvoiceId()));

        final BundleTimelineJson bundleTimelineJson = new BundleTimelineJson(viewId,
                                                                             bundleJsonWithSubscriptions,
                                                                             ImmutableList.<PaymentJsonSimple>of(paymentJsonSimple),
                                                                             ImmutableList.<InvoiceJsonSimple>of(invoiceJsonSimple),
                                                                             reason);

        final String asJson = mapper.writeValueAsString(bundleTimelineJson);
        final BundleTimelineJson fromJson = mapper.readValue(asJson, BundleTimelineJson.class);
        Assert.assertEquals(fromJson, bundleTimelineJson);
    }

    private BundleJsonWithSubscriptions createBundleWithSubscriptions() {
        final DateTime effectiveDate = clock.getUTCNow();
        final UUID eventId = UUID.randomUUID();
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(UUID.randomUUID().toString(), ProductCategory.BASE,
                                                                             BillingPeriod.NO_BILLING_PERIOD, UUID.randomUUID().toString(),
                                                                             PhaseType.EVERGREEN);
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID entitlementId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();


        final SubscriptionJsonWithEvents entitlementJsonWithEvents = new SubscriptionJsonWithEvents(accountId, bundleId, entitlementId, externalKey, ImmutableList.<SubscriptionEvent>of(), ImmutableList.<AuditLog>of(), ImmutableMap.<UUID, List<AuditLog>>of());
        return new BundleJsonWithSubscriptions(bundleId.toString(), externalKey, ImmutableList.<SubscriptionJsonWithEvents>of(entitlementJsonWithEvents), null);
    }

    private InvoiceJsonSimple createInvoice() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final BigDecimal invoiceAmount = BigDecimal.TEN;
        final BigDecimal creditAdj = BigDecimal.ONE;
        final BigDecimal refundAdj = BigDecimal.ONE;
        final LocalDate invoiceDate = clock.getUTCToday();
        final LocalDate targetDate = clock.getUTCToday();
        final String invoiceNumber = UUID.randomUUID().toString();
        final BigDecimal balance = BigDecimal.ZERO;

        return new InvoiceJsonSimple(invoiceAmount, Currency.USD.toString(), creditAdj, refundAdj, invoiceId.toString(), invoiceDate,
                                     targetDate, invoiceNumber, balance, accountId.toString(), null);
    }

    private PaymentJsonSimple createPayment(final UUID accountId, final UUID invoiceId) {
        final UUID paymentId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal paidAmount = BigDecimal.TEN;
        final BigDecimal amount = BigDecimal.ZERO;
        final DateTime paymentRequestedDate = clock.getUTCNow();
        final DateTime paymentEffectiveDate = clock.getUTCNow();
        final Integer retryCount = Integer.MAX_VALUE;
        final String currency = "USD";
        final String status = UUID.randomUUID().toString();
        final String gatewayErrorCode = "OK";
        final String gatewayErrorMsg = "Excellent...";
        final String extFirstPaymentIdRef = UUID.randomUUID().toString();
        final String extSecondPaymentIdRef = UUID.randomUUID().toString();

        return new PaymentJsonSimple(amount, paidAmount, accountId.toString(), invoiceId.toString(), paymentId.toString(),
                                     paymentMethodId.toString(), paymentRequestedDate, paymentEffectiveDate, retryCount, currency, status,
                                     gatewayErrorCode, gatewayErrorMsg, extFirstPaymentIdRef, extSecondPaymentIdRef, null);
    }
}
