/*
 * Copyright 2010-2011 Ning, Inc.
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

import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.map.SerializationConfig;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.SubscriptionJson.SubscriptionDeletedEventJson;
import com.ning.billing.jaxrs.json.SubscriptionJson.SubscriptionNewEventJson;
import com.ning.billing.jaxrs.json.SubscriptionJson.SubscriptionReadEventJson;

public class TestJsonViews {


    public static final Logger log = LoggerFactory.getLogger(TestJsonViews.class);

    private ObjectMapper objectMapper;

    @BeforeClass(groups="fast")
    public void setup() {
        objectMapper = new ObjectMapper().configure(SerializationConfig.Feature.DEFAULT_VIEW_INCLUSION, false);
        objectMapper.disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
    }



    @Test(groups="fast")
    public void testSubscriptionBaseView() {
        testSubscriptionView(BundleTimelineViews.Base.class);
    }

    @Test(groups="fast")
    public void testSubscriptionTimelineView() {
        testSubscriptionView(BundleTimelineViews.ReadTimeline.class);
    }

    private void testSubscriptionView(Class<? extends BundleTimelineViews.Base> viewClass) {
        try {

            SubscriptionJson obj = buildSubscriptionReadEventJson();

            ObjectWriter objWriter = objectMapper.writerWithView(viewClass);

            Writer writer = new StringWriter();
            objWriter.writeValue(writer, obj);
            String baseJson = writer.toString();

            log.info(baseJson);

            SubscriptionJson objFromJson = objectMapper.readValue(baseJson, SubscriptionJson.class);

            log.info(objFromJson.toString());

            if (viewClass.equals(BundleTimelineViews.Base.class)) {
                Assert.assertNull(objFromJson.getEvents());
            } else {
                Assert.assertNotNull(objFromJson.getEvents());
            }

            writer = new StringWriter();
            objWriter.writeValue(writer, objFromJson);
            String newBaseJson = writer.toString();

            Assert.assertEquals(newBaseJson, baseJson);

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups="fast")
    public void testBundleReadTimelineJson() {
        testBundleTimelineJson(BundleTimelineViews.ReadTimeline.class);
    }

    @Test(groups="fast")
    public void testBundleWriteTimelineJson() {
        testBundleTimelineJson(BundleTimelineViews.WriteTimeline.class);
    }

    private void testBundleTimelineJson(Class<? extends BundleTimelineViews.Base> viewClass) {

        final boolean readTimeline = viewClass.equals(BundleTimelineViews.ReadTimeline.class);
        try {

            BundleTimelineJson obj = buildBundleTimelineJson(readTimeline);

            ObjectWriter objWriter = objectMapper.writerWithView(viewClass);

            Writer writer = new StringWriter();
            objWriter.writeValue(writer, obj);
            String baseJson = writer.toString();

            log.info(baseJson);

            BundleTimelineJson objFromJson = objectMapper.readValue(baseJson, BundleTimelineJson.class);

            log.info(objFromJson.toString());

            Assert.assertNotNull(objFromJson.getViewId());
            Assert.assertNotNull(objFromJson.getBundle());

            BundleJson bundle = objFromJson.getBundle();
            Assert.assertNotNull(bundle.getBundleId());
            Assert.assertNotNull(bundle.getAccountId());
            Assert.assertNotNull(bundle.getExternalKey());
            Assert.assertNotNull(bundle.getSubscriptions());

            List<SubscriptionJson> subscriptions = bundle.getSubscriptions();
            Assert.assertEquals(subscriptions.size(), 1);
            SubscriptionJson sub = subscriptions.get(0);
            Assert.assertNotNull(sub.getBundleId());
            Assert.assertNotNull(sub.getBillingPeriod());
            Assert.assertNotNull(sub.getPriceList());
            Assert.assertNotNull(sub.getProductCategory());
            Assert.assertNotNull(sub.getProductName());
            Assert.assertNotNull(sub.getSubscriptionId());

            List<SubscriptionReadEventJson> events = sub.getEvents();
            List<SubscriptionDeletedEventJson> deletedEvents = sub.getDeletedEvents();
            List<SubscriptionNewEventJson> newEvents = sub.getNewEvents();

            if (viewClass.equals(BundleTimelineViews.WriteTimeline.class)) {
                Assert.assertNull(objFromJson.getPayments());
                Assert.assertNull(objFromJson.getInvoices());
                Assert.assertNull(objFromJson.getInvoices());

                Assert.assertNull(events);
                Assert.assertEquals(newEvents.size(), 1);
                for (SubscriptionNewEventJson cur : newEvents) {
                    Assert.assertNotNull(cur.getRequestedDate());
                    Assert.assertNotNull(cur.getEventType());
                    Assert.assertNotNull(cur.getBillingPeriod());
                    Assert.assertNotNull(cur.getPhase());
                    Assert.assertNotNull(cur.getPriceList());
                    Assert.assertNotNull(cur.getProduct());
                }


                Assert.assertEquals(deletedEvents.size(), 1);
                for (SubscriptionDeletedEventJson cur : deletedEvents) {
                    Assert.assertNotNull(cur.getEventId());
                    Assert.assertNotNull(cur.getEffectiveDate());
                    Assert.assertNotNull(cur.getRequestedDate());
                    Assert.assertNotNull(cur.getEventType());
                    Assert.assertNotNull(cur.getBillingPeriod());
                    Assert.assertNotNull(cur.getPhase());
                    Assert.assertNotNull(cur.getPriceList());
                    Assert.assertNotNull(cur.getProduct());
                }


                Assert.assertNotNull(objFromJson.getResonForChange());

            } else if (viewClass.equals(BundleTimelineViews.ReadTimeline.class)) {
                Assert.assertNotNull(objFromJson.getPayments());
                Assert.assertNotNull(objFromJson.getInvoices());
                Assert.assertNotNull(objFromJson.getInvoices());

                Assert.assertNull(newEvents);
                Assert.assertNull(deletedEvents);
                Assert.assertEquals(events.size(), 2);
                for (SubscriptionReadEventJson cur : events) {
                    Assert.assertNotNull(cur.getEventId());
                    Assert.assertNotNull(cur.getEffectiveDate());
                    Assert.assertNotNull(cur.getRequestedDate());
                    Assert.assertNotNull(cur.getEventType());
                    Assert.assertNotNull(cur.getBillingPeriod());
                    Assert.assertNotNull(cur.getPhase());
                    Assert.assertNotNull(cur.getPriceList());
                    Assert.assertNotNull(cur.getProduct());
                }

                Assert.assertNull(objFromJson.getResonForChange());
            } else {
                Assert.fail("View of no interest");
            }

            writer = new StringWriter();
            objWriter.writeValue(writer, objFromJson);
            String newBaseJson = writer.toString();

            Assert.assertEquals(newBaseJson, baseJson);

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }

    private BundleTimelineJson buildBundleTimelineJson(boolean readTimeline) {

        final String reason = "whatever";
        final String viewId = "view-123356";
        final BundleJson bundle = buildBundle(readTimeline);
        final List<PaymentJson> payments = new LinkedList<PaymentJson>();
        payments.add(buildPaymentJson());

        final List<InvoiceJson> invoices = new LinkedList<InvoiceJson>();
        invoices.add(buildInvoiceJson());
        return new BundleTimelineJson(viewId, bundle, payments, invoices, reason);
    }


    private BundleJson buildBundle(boolean readTimeline) {
        final String bundleId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String externalKey = "bozo-the-sioux";
        final List<SubscriptionJson> subscriptions = new LinkedList<SubscriptionJson>();
        subscriptions.add(readTimeline ? buildSubscriptionReadEventJson() : buildSubscriptionWriteEventJson());
        return new BundleJson(bundleId, accountId, externalKey, subscriptions);
    }
    private PaymentJson buildPaymentJson() {

        final BigDecimal paidAmount = new BigDecimal(128.56);
        final String invoiceId = UUID.randomUUID().toString();
        final String paymentId = UUID.randomUUID().toString();
        final DateTime requestedDate =  new DateTime();
        final DateTime effectiveDate = requestedDate;
        final String status = "Success";
        return new PaymentJson(paidAmount, invoiceId, paymentId, requestedDate, effectiveDate, status);
    }

    private InvoiceJson buildInvoiceJson() {
        final BigDecimal amount = new BigDecimal(128.56);
        final BigDecimal balance = new BigDecimal(0.0);
        final String invoiceId = UUID.randomUUID().toString();
        final String invoiceNumber =  "INV-00012";
        final DateTime requestedDate =  new DateTime();
        return new InvoiceJson(amount, invoiceId, requestedDate, invoiceNumber, balance);
    }

    private SubscriptionJson buildSubscriptionReadEventJson() {

        final List<SubscriptionReadEventJson> events = new LinkedList<SubscriptionReadEventJson>();

        final String eventId1 = UUID.randomUUID().toString();
        final String productName1 = "gold";
        final String billingPeriod1 = "monthly";
        final DateTime requestedDate1 =  new DateTime();
        final DateTime effectiveDate1 = requestedDate1;
        final String priceList1 = "default";
        final String eventType1 = "CREATE";
        final String phase1 = "TRIAL";
        SubscriptionReadEventJson ev1 = new SubscriptionReadEventJson(eventId1, billingPeriod1, requestedDate1, effectiveDate1, productName1, priceList1, eventType1, phase1);
        events.add(ev1);

        final String eventId2 = UUID.randomUUID().toString();
        final String productName2 = "gold";
        final String billingPeriod2 = "monthly";
        final DateTime requestedDate2 =  new DateTime();
        final DateTime effectiveDate2 = requestedDate2;
        final String priceList2 = "default";
        final String eventType2 = "PHASE";
        final String phase2 = "EVERGREEN";
        SubscriptionReadEventJson ev2 = new SubscriptionReadEventJson(eventId2, billingPeriod2, requestedDate2, effectiveDate2, productName2, priceList2, eventType2, phase2);
        events.add(ev2);


        final String subscriptionId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String productName = productName2;
        final String productCategory = "classic";
        final String billingPeriod = billingPeriod2;
        final String priceList = priceList2;

        SubscriptionJson obj = new SubscriptionJson(subscriptionId, bundleId, productName, productCategory, billingPeriod, priceList, events, null, null);
        return obj;
    }


    private SubscriptionJson buildSubscriptionWriteEventJson() {

        final List<SubscriptionNewEventJson> newEvents = new LinkedList<SubscriptionNewEventJson>();

        final String eventId1 = UUID.randomUUID().toString();
        final String productName1 = "gold";
        final String billingPeriod1 = "monthly";
        final DateTime requestedDate1 =  new DateTime();
        final DateTime effectiveDate1 = requestedDate1;
        final String priceList1 = "default";
        final String eventType1 = "CREATE";
        final String phase1 = "TRIAL";
        SubscriptionNewEventJson ev1 = new SubscriptionNewEventJson(billingPeriod1, requestedDate1, productName1, priceList1, eventType1, phase1);
        newEvents.add(ev1);

        final List<SubscriptionDeletedEventJson> deletedEvents = new LinkedList<SubscriptionDeletedEventJson>();
        final String eventId2 = UUID.randomUUID().toString();
        final String productName2 = "gold";
        final String billingPeriod2 = "monthly";
        final DateTime requestedDate2 =  new DateTime();
        final DateTime effectiveDate2 = requestedDate2;
        final String priceList2 = "default";
        final String eventType2 = "PHASE";
        final String phase2 = "EVERGREEN";
        SubscriptionDeletedEventJson ev2 = new SubscriptionDeletedEventJson(eventId2, billingPeriod2, requestedDate2, effectiveDate2, productName2, priceList2, eventType2, phase2);
        deletedEvents.add(ev2);


        final String subscriptionId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String productName = productName2;
        final String productCategory = "classic";
        final String billingPeriod = billingPeriod2;
        final String priceList = priceList2;

        SubscriptionJson obj = new SubscriptionJson(subscriptionId, bundleId, productName, productCategory, billingPeriod, priceList, null, newEvents, deletedEvents);
        return obj;
    }


}
