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
        final InvoiceCreationInternalEvent e = new DefaultInvoiceCreationEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(12.0), Currency.USD, 1L, 2L, null);
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
        final NullInvoiceInternalEvent e = new DefaultNullInvoiceEvent(UUID.randomUUID(), new LocalDate(), 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);

        final Object obj = mapper.readValue(json, DefaultNullInvoiceEvent.class);
        Assert.assertEquals(obj, e);
    }
}
