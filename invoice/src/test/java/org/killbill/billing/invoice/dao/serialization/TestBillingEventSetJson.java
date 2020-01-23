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

package org.killbill.billing.invoice.dao.serialization;

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
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.MockBillingEventSet;
import org.killbill.billing.invoice.dao.serialization.BillingEventSetJson.BillingEventJson;
import org.killbill.billing.invoice.dao.serialization.BillingEventSetJson.SubscriptionBillingEventJson;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTEEN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

public class TestBillingEventSetJson extends InvoiceTestSuiteNoDB {

    private ObjectMapper testMapper;

    @BeforeClass(groups = "fast")
    public void setup() {
        testMapper = new ObjectMapper();
        testMapper.registerModule(new JodaModule());
        testMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    @Test(groups = "fast")
    public void testTestBillingEventSetJsonSerialization() throws Exception {

        final LocalDate startDate = new LocalDate(2019, 9, 26);
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final Currency currency = Currency.USD;

        final SubscriptionBase sub = Mockito.mock(SubscriptionBase.class);
        Mockito.when(sub.getId()).thenReturn(subscriptionId);
        Mockito.when(sub.getBundleId()).thenReturn(bundleId);

        final MockBillingEventSet billingEventSet = new MockBillingEventSet();
        final Plan plan = new MockPlan("Test");
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(new DefaultPrice(THIRTEEN, Currency.USD));
        final PlanPhase planPhase = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final DateTime effectiveDate1 = startDate.toDateTimeAtStartOfDay();
        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, sub, effectiveDate1, plan, planPhase,
                                                                       planPhase.getFixed().getPrice() == null ? null : planPhase.getFixed().getPrice().getPrice(currency),
                                                                       planPhase.getRecurring().getRecurringPrice() == null ? null : planPhase.getRecurring().getRecurringPrice().getPrice(currency),
                                                                       currency, planPhase.getRecurring().getBillingPeriod(),
                                                                       1, BillingMode.IN_ADVANCE, "Test", 1, SubscriptionBaseTransitionType.CREATE);
        billingEventSet.add(event1);

        final DateTime effectiveDate2 = startDate.toDateTimeAtStartOfDay().plusDays(10);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, sub, effectiveDate2, plan, planPhase,
                                                                       planPhase.getFixed().getPrice() == null ? null : planPhase.getFixed().getPrice().getPrice(currency),
                                                                       planPhase.getRecurring().getRecurringPrice() == null ? null : planPhase.getRecurring().getRecurringPrice().getPrice(currency),
                                                                       currency, planPhase.getRecurring().getBillingPeriod(),
                                                                       1, BillingMode.IN_ADVANCE, "Test", 1, SubscriptionBaseTransitionType.CHANGE);
        billingEventSet.add(event2);

        final BillingEventSetJson json = new BillingEventSetJson(billingEventSet);

        final String asJson = testMapper.writeValueAsString(json);
        final BillingEventSetJson res = testMapper.readValue(asJson, BillingEventSetJson.class);

        assertFalse(res.isAutoInvoiceOff());
        assertFalse(res.isAutoInvoiceDraft());
        assertFalse(res.isAutoInvoiceReuseDraft());
        assertEquals(res.getSubscriptionEvents().size(), 1);

        final SubscriptionBillingEventJson res1 = res.getSubscriptionEvents().get(0);
        assertEquals(res1.getSubscriptionId(), subscriptionId);
        assertEquals(res1.getEvents().size(), 2);

        final BillingEventJson billingEvent1 = res1.getEvents().get(0);
        assertEquals(billingEvent1.getBcdLocal(), 1);
        assertEquals(billingEvent1.getPlanName(), "Test");
        assertEquals(billingEvent1.getPhaseName(), "Test-evergreen");
        assertEquals(billingEvent1.getEffDate().compareTo(effectiveDate1), 0);
        assertNull(billingEvent1.getFixedPrice());
        assertEquals(billingEvent1.getRecurringPrice().compareTo(new BigDecimal("13.0")), 0);
        assertEquals(billingEvent1.getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        // Our Mock implementation returns null
        assertNull(billingEvent1.getCatalogEffDt());

    }

}
