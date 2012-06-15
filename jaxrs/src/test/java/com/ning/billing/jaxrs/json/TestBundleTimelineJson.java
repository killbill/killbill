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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.ImmutableList;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.util.clock.DefaultClock;

public class TestBundleTimelineJson {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

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

        final SubscriptionJsonWithEvents subscription = bundleTimelineJson.getBundle().getSubscriptions().get(0);
        final SubscriptionJsonWithEvents.SubscriptionReadEventJson event = subscription.getEvents().get(0);
        final PaymentJsonSimple payment = bundleTimelineJson.getPayments().get(0);
        final InvoiceJsonSimple invoice = bundleTimelineJson.getInvoices().get(0);

        Assert.assertEquals(asJson, "{\"viewId\":\"" + bundleTimelineJson.getViewId() + "\"," +
                "\"bundle\":{\"bundleId\":\"" + bundleTimelineJson.getBundle().getBundleId() + "\"," +
                "\"externalKey\":\"" + bundleTimelineJson.getBundle().getExternalKey() + "\"," +
                "\"subscriptions\":" +
                "[{\"events\":[{\"eventId\":\"" + event.getEventId() + "\"," +
                "\"billingPeriod\":\"" + event.getBillingPeriod() + "\"," +
                "\"product\":\"" + event.getProduct() + "\"," +
                "\"priceList\":\"" + event.getPriceList() + "\"," +
                "\"eventType\":\"" + event.getEventType() + "\"," +
                "\"phase\":\"" + event.getPhase() + "\"," +
                "\"requestedDate\":null," +
                "\"effectiveDate\":\"" + event.getEffectiveDate().toDateTimeISO().toString() + "\"}]," +
                "\"subscriptionId\":\"" + subscription.getSubscriptionId() + "\"," +
                "\"deletedEvents\":null," +
                "\"newEvents\":null}]}," +
                "\"payments\":[{\"amount\":" + payment.getAmount() + "," +
                "\"paidAmount\":" + payment.getPaidAmount() + "," +
                "\"accountId\":\"" + payment.getAccountId() + "\"," +
                "\"invoiceId\":\"" + payment.getInvoiceId() + "\"," +
                "\"paymentId\":\"" + payment.getPaymentId() + "\"," +
                "\"requestedDate\":\"" + payment.getRequestedDate().toDateTimeISO().toString() + "\"," +
                "\"effectiveDate\":\"" + payment.getEffectiveDate().toDateTimeISO().toString() + "\"," +
                "\"retryCount\":" + payment.getRetryCount() + "," +
                "\"currency\":\"" + payment.getCurrency() + "\"," +
                "\"status\":\"" + payment.getStatus() + "\"}]," +
                "\"invoices\":[{\"amount\":" + invoice.getAmount() + "," +
                "\"credit\":" + invoice.getCredit() + "," +
                "\"invoiceId\":\"" + invoice.getInvoiceId() + "\"," +
                "\"invoiceDate\":\"" + invoice.getInvoiceDate().toDateTimeISO().toString() + "\"," +
                "\"targetDate\":\"" + invoice.getTargetDate() + "\"," +
                "\"invoiceNumber\":\"" + invoice.getInvoiceNumber() + "\"," +
                "\"balance\":" + invoice.getBalance() + "," +
                "\"accountId\":\"" + invoice.getAccountId() + "\"}]," +
                "\"reasonForChange\":\"" + reason + "\"}");

        final BundleTimelineJson fromJson = mapper.readValue(asJson, BundleTimelineJson.class);
        Assert.assertEquals(fromJson, bundleTimelineJson);
    }

    private BundleJsonWithSubscriptions createBundleWithSubscriptions() {
        final SubscriptionTimeline.ExistingEvent event = Mockito.mock(SubscriptionTimeline.ExistingEvent.class);
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(UUID.randomUUID().toString(), ProductCategory.BASE,
                                                                             BillingPeriod.NO_BILLING_PERIOD, UUID.randomUUID().toString(),
                                                                             PhaseType.EVERGREEN);
        Mockito.when(event.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(event.getEventId()).thenReturn(eventId);
        Mockito.when(event.getSubscriptionTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(event.getPlanPhaseSpecifier()).thenReturn(planPhaseSpecifier);

        final SubscriptionTimeline subscriptionTimeline = Mockito.mock(SubscriptionTimeline.class);
        Mockito.when(subscriptionTimeline.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionTimeline.getExistingEvents()).thenReturn(ImmutableList.<SubscriptionTimeline.ExistingEvent>of(event));

        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final SubscriptionJsonWithEvents subscription = new SubscriptionJsonWithEvents(bundleId, subscriptionTimeline);

        return new BundleJsonWithSubscriptions(bundleId.toString(), externalKey, ImmutableList.<SubscriptionJsonWithEvents>of(subscription));
    }

    private InvoiceJsonSimple createInvoice() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final BigDecimal invoiceAmount = BigDecimal.TEN;
        final BigDecimal credit = BigDecimal.ONE;
        final DateTime invoiceDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime targetDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final String invoiceNumber = UUID.randomUUID().toString();
        final BigDecimal balance = BigDecimal.ZERO;

        return new InvoiceJsonSimple(invoiceAmount, credit, invoiceId.toString(), invoiceDate,
                                     targetDate, invoiceNumber, balance, accountId.toString());
    }

    private PaymentJsonSimple createPayment(final UUID accountId, final UUID invoiceId) {
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal paidAmount = BigDecimal.TEN;
        final BigDecimal amount = BigDecimal.ZERO;
        final DateTime paymentRequestedDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime paymentEffectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final Integer retryCount = Integer.MAX_VALUE;
        final String currency = "USD";
        final String status = UUID.randomUUID().toString();


        return new PaymentJsonSimple(amount, paidAmount, accountId.toString(), invoiceId.toString(), paymentId.toString(),
                                     paymentRequestedDate, paymentEffectiveDate, retryCount,
                                     currency, status);
    }
}
