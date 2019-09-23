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

package org.killbill.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoiceNotificationInternalEvent;
import org.killbill.billing.events.NullInvoiceInternalEvent;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.MockBillingEventSet;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.invoice.TestInvoiceHelper.TEN;

public class TestEventJson extends InvoiceTestSuiteNoDB {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testInvoiceCreationEvent() throws Exception {
        final byte [] lzBillingEvents = null;
        final InvoiceCreationInternalEvent e = new DefaultInvoiceCreationEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.0), Currency.USD, lzBillingEvents, 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);
        final Object obj = mapper.readValue(json, DefaultInvoiceCreationEvent.class);
        Assert.assertEquals(obj, e);
    }


    @Test(groups = "fast")
    public void testInvoiceCreationEventWithBillingEvents() throws Exception {

        final BillingEventSet events = new MockBillingEventSet();

        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final SubscriptionBase sub = Mockito.mock(SubscriptionBase.class);
        Mockito.when(sub.getId()).thenReturn(subscriptionId);
        Mockito.when(sub.getBundleId()).thenReturn(bundleId);
        final LocalDate startDate = invoiceUtil.buildDate(2011, 9, 1);

        final Plan plan = new MockPlan();
        final BigDecimal rate1 = TEN;
        final PlanPhase phase = new MockPlanPhase(new MockInternationalPrice(new DefaultPrice(rate1, Currency.USD)),
                                                  null, BillingPeriod.THIRTY_DAYS);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(null, sub, startDate.toDateTimeAtStartOfDay(), plan, phase,
                                                                      phase.getFixed().getPrice() == null ? null : phase.getFixed().getPrice().getPrice(Currency.USD),
                                                                      phase.getRecurring().getRecurringPrice() == null ? null : phase.getRecurring().getRecurringPrice().getPrice(Currency.USD),
                                                                      Currency.USD, phase.getRecurring().getBillingPeriod(),
                                                                      1, BillingMode.IN_ADVANCE, "Test", 1, SubscriptionBaseTransitionType.CREATE);
        events.add(event);


        final byte [] lzBillingEvents = "\"WlYBA1QJcRd7ImF1dG9JbnZvaWNlT2ZmIjpmYWxzZSzgAxYERHJhZnTgCxgDUmV1c+AGHQhldmVudHMiOlvgEFgfc3Vic2NyaXB0aW9uSWQiOiI2MzUzMDk2Yy0zZGZhLTQVM2U1LWFhNWEtYzNkYjk0NTc2ZmZjIkBZoFcKYmNkTG9jYWwiOjEgpgRsaWdubSBzIEgGQUNDT1VOVCArB3BsYW5OYW1lIBQOc2hvdGd1bi1tb250aGx5QBwDaGFzZeANHQMtdHJpIFgNLCJiaWxsaW5nUGVyaW9AoxBOT19CSUxMSU5HX1BFUklPRCAjBWVmZkRhdCBjAXsiYFwLT2ZZZWFyIjo0LCJtQD4Hc09mU2Vjb24gPwMwLCJjIKMKdXJ5T2ZFcmEiOjIgEQB5IC3gAA4BMTLAEABDgCgg3SASA3dlZWtAJ0AxwA8CT2ZX4AAVBTEzLCJkYSBVgHoAOSAyAmhvdSBWAURhIEEgZARtaW51dCHYAEggFWAQAHNgkgJPZk1gGAMiOjM44AETYDRAEMA1YBBgRSEOIM9gDwMzODAwIBNgdkCJAiI6NyEMQNUhj+ADrGAhAE1Bd2AaCGNocm9ub2xvZyCZBHsiem9uIH0FeyJmaXhlISECdHJ1InQAaSAJByJVVEMifX0s4BchQKVAlSBbBjMzMzIzODSgmCKsBGVyTm934gCxBWJlZm9yZWARgGYEZXF1YWxgD2LsIEpghQFQciMDYO8FcmVjdXJyQgCAEgtudWxsLCJ0cmFuc2lCtwJUeXAgKAYiQ1JFQVRFIgoHY2F0YWxvZ0UiESKI4gYPIRWAqeIkDyA1QUviAw8gEuIEDyGEQA+BhUAsAiI6NSIAYXaCDyAx4iYOIFPCIWHsIA/iFAwgH2BvgggANiFb4gcIIINgIeJbCAEyOSIGIp1AAOIrCAF9LORVXiUdBXJncmVlbiIzAGJCveQCYgZNT05USExZIBnkD1gANUGLQvLiJEgiEUIb4gNIIBJCOEAWACIkisAPgcWAFQAxJBZhtqJIADIgM+ImSkRIAHOkbWJLQBDiFEyjwGB3glAgdeIIUCATYCHiW1AFMzM1ODMw5D9ZhEbkB1wEMjQ5Ljkh2+QIXgNQSEFT5P9dRmbkuF0DXX1dfQ==\",\"searchKey1\":1,\"searchKey2\":0,\"userToken\":\"0f3140ee-a6f5-4c16-b95d-da2916e7f14e\"".getBytes();
        final InvoiceCreationInternalEvent e = new DefaultInvoiceCreationEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.0), Currency.USD, lzBillingEvents, 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);
        final Object obj = mapper.readValue(json, DefaultInvoiceCreationEvent.class);
        Assert.assertEquals(obj, e);
    }



    @Test(groups = "fast")
    public void testInvoiceNotificationEvent() throws Exception {

        final InvoiceNotificationInternalEvent e = new DefaultInvoiceNotificationInternalEvent(UUID.randomUUID(),  new BigDecimal(12.0), Currency.USD, new DateTime(), 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);

        final Object obj = mapper.readValue(json, DefaultInvoiceNotificationInternalEvent.class);
        Assert.assertEquals(obj, e);
    }

    @Test(groups = "fast")
    public void testEmptyInvoiceEvent() throws Exception {
        final byte [] lzBillingEvents = null;
        final NullInvoiceInternalEvent e = new DefaultNullInvoiceEvent(UUID.randomUUID(), lzBillingEvents, new LocalDate(), 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);

        final Object obj = mapper.readValue(json, DefaultNullInvoiceEvent.class);
        Assert.assertEquals(obj, e);
    }
}
