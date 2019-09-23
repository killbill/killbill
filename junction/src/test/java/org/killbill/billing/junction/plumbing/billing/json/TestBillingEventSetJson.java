/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing.json;

import java.math.BigDecimal;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.junction.plumbing.billing.DefaultBillingEventSet;
import org.killbill.billing.junction.plumbing.billing.json.BillingEventSetJson.SubscriptionBillingEventJson;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestBillingEventSetJson extends JunctionTestSuiteNoDB {

    @Inject
    protected ObjectMapper mapper;

    @BeforeClass(groups = "fast")
    public void setup() {
        mapper.registerModule(new JodaModule());
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    @Test(groups = "fast")
    public void testTestBillingEventSetJsonSerialization() throws Exception {

        final SortedSet<BillingEvent> set = new TreeSet<BillingEvent>();

        final BillingEvent event1Sub1 = createEvent(subscription(new UUID(0L, 1L)), new DateTime("2018-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        final BillingEvent event2Sub1 = createEvent(subscription(new UUID(0L, 1L)), new DateTime("2018-01-31T00:02:04.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event3Sub1 = createEvent(subscription(new UUID(0L, 1L)), new DateTime("2018-01-31T00:02:05.000Z"), SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event4Sub1 = createEvent(subscription(new UUID(0L, 1L)), new DateTime("2018-01-31T00:02:05.000Z"), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        set.add(event1Sub1);
        set.add(event2Sub1);
        set.add(event3Sub1);
        set.add(event4Sub1);

        final BillingEvent event1Sub2 = createEvent(subscription(new UUID(0L, 2L)), new DateTime("2019-02-21T00:03:02.000Z"), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        final BillingEvent event2Sub2 = createEvent(subscription(new UUID(0L, 2L)), new DateTime("2019-02-21T00:04:02.000Z"), SubscriptionBaseTransitionType.CREATE);
        final BillingEvent event3Sub2 = createEvent(subscription(new UUID(0L, 2L)), new DateTime("2019-02-26T00:05:01.000Z"), SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event4Sub2 = createEvent(subscription(new UUID(0L, 2L)), new DateTime("2019-03-05T00:06:07.000Z"), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        set.add(event1Sub2);
        set.add(event2Sub2);
        set.add(event3Sub2);
        set.add(event4Sub2);

        final BillingEventSet billingEventSet = new DefaultBillingEventSet(true, false, false);
        billingEventSet.addAll(set);
        billingEventSet.getSubscriptionIdsWithAutoInvoiceOff().add(new UUID(0L, 2L));

        final BillingEventSetJson json = new BillingEventSetJson(billingEventSet);

        final String asJson = mapper.writeValueAsString(json);
        final BillingEventSetJson res = mapper.readValue(asJson, BillingEventSetJson.class);

        assertEquals(res.getEvents().size(), 2);
        assertTrue(res.isAutoInvoiceOff());
        assertFalse(res.isAutoInvoiceDraft());
        assertFalse(res.isAutoInvoiceReuseDraft());

        final SubscriptionBillingEventJson res1 = res.getEvents().get(0);
        assertEquals(res1.getSubscriptionId(), new UUID(0L, 1L));
        assertEquals(res1.getEvents().size(), 4);

        assertEquals(res1.getEvents().get(0).getBcdLocal(), 1);
        assertEquals(res1.getEvents().get(0).getPlanName(), "1-BicycleTrialEvergreen1USD");
        assertEquals(res1.getEvents().get(0).getPhaseName(), "Test-trial");
        assertEquals(res1.getEvents().get(0).getEffDate().compareTo(new DateTime("2018-01-31T00:02:04.000Z")), 0);
        assertEquals(res1.getEvents().get(0).getFixedPrice(), BigDecimal.ZERO);
        assertEquals(res1.getEvents().get(0).getRecurringPrice(), BigDecimal.ZERO);
        assertEquals(res1.getEvents().get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertNotNull(res1.getEvents().get(0).getCatalogEffDt());

    }

}
