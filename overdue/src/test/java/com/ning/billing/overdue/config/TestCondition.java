/*
00 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.overdue.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.OverdueTestSuite;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.catalog.XMLLoader;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TestCondition extends OverdueTestSuite {

    @XmlRootElement(name = "condition")
    private static class MockCondition extends DefaultCondition<Blockable> {}

    private Clock clock = new ClockMock();

    @Test(groups = "fast")
    public void testNumberOfUnpaidInvoicesEqualsOrExceeds() throws Exception {
        final String xml =
                "<condition>" +
                "	<numberOfUnpaidInvoicesEqualsOrExceeds>1</numberOfUnpaidInvoicesEqualsOrExceeds>" +
                "</condition>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is, MockCondition.class);
        final UUID unpaidInvoiceId = UUID.randomUUID();

        final BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L, 1L), 0, BigDecimal.ZERO, new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L, 1L), 1, BigDecimal.ZERO, new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L, 1L), 2, BigDecimal.ZERO, new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});

        Assert.assertTrue(!c.evaluate(state0, new LocalDate()));
        Assert.assertTrue(c.evaluate(state1, new LocalDate()));
        Assert.assertTrue(c.evaluate(state2, new LocalDate()));
    }

    @Test(groups = "fast")
    public void testTotalUnpaidInvoiceBalanceEqualsOrExceeds() throws Exception {
        final String xml =
                "<condition>" +
                "	<totalUnpaidInvoiceBalanceEqualsOrExceeds>100.00</totalUnpaidInvoiceBalanceEqualsOrExceeds>" +
                "</condition>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is, MockCondition.class);
        final UUID unpaidInvoiceId = UUID.randomUUID();

        final BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L, 1L), 0, BigDecimal.ZERO, new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("100.00"), new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("200.00"), new LocalDate(),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});

        Assert.assertTrue(!c.evaluate(state0, new LocalDate()));
        Assert.assertTrue(c.evaluate(state1, new LocalDate()));
        Assert.assertTrue(c.evaluate(state2, new LocalDate()));
    }

    @Test(groups = "fast")
    public void testTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() throws Exception {
        final String xml =
                "<condition>" +
                "	<timeSinceEarliestUnpaidInvoiceEqualsOrExceeds><unit>DAYS</unit><number>10</number></timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                "</condition>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is, MockCondition.class);
        final UUID unpaidInvoiceId = UUID.randomUUID();

        final LocalDate now = new LocalDate();

        final BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L, 1L), 0, BigDecimal.ZERO, null,
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("100.00"), now.minusDays(10),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("200.00"), now.minusDays(20),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});

        Assert.assertTrue(!c.evaluate(state0, now));
        Assert.assertTrue(c.evaluate(state1, now));
        Assert.assertTrue(c.evaluate(state2, now));
    }

    @Test(groups = "fast")
    public void testResponseForLastFailedPaymentIn() throws Exception {
        final String xml =
                "<condition>" +
                "	<responseForLastFailedPaymentIn><response>INSUFFICIENT_FUNDS</response><response>DO_NOT_HONOR</response></responseForLastFailedPaymentIn>" +
                "</condition>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is, MockCondition.class);
        final UUID unpaidInvoiceId = UUID.randomUUID();

        final LocalDate now = new LocalDate();

        final BillingState<Blockable> state0 = new BillingState<Blockable>(new UUID(0L, 1L), 0, BigDecimal.ZERO, null,
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.LOST_OR_STOLEN_CARD, new Tag[]{});
        final BillingState<Blockable> state1 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("100.00"), now.minusDays(10),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS, new Tag[]{});
        final BillingState<Blockable> state2 = new BillingState<Blockable>(new UUID(0L, 1L), 1, new BigDecimal("200.00"), now.minusDays(20),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.DO_NOT_HONOR, new Tag[]{});

        Assert.assertTrue(!c.evaluate(state0, now));
        Assert.assertTrue(c.evaluate(state1, now));
        Assert.assertTrue(c.evaluate(state2, now));
    }

    @Test(groups = "fast")
    public void testHasControlTag() throws Exception {
        final String xml =
                "<condition>" +
                "	<controlTag>OVERDUE_ENFORCEMENT_OFF</controlTag>" +
                "</condition>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final MockCondition c = XMLLoader.getObjectFromStreamNoValidation(is, MockCondition.class);
        final UUID unpaidInvoiceId = UUID.randomUUID();

        final LocalDate now = new LocalDate();

        final ObjectType objectType = ObjectType.BUNDLE;

        final UUID objectId = new UUID(0L, 1L);
        final BillingState<Blockable> state0 = new BillingState<Blockable>(objectId, 0, BigDecimal.ZERO, null,
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.LOST_OR_STOLEN_CARD,
                                                                           new Tag[]{new DefaultControlTag( ControlTagType.AUTO_INVOICING_OFF, objectType, objectId, clock.getUTCNow()),
                                                                                   new DescriptiveTag(UUID.randomUUID(), objectType, objectId, clock.getUTCNow())});

        final BillingState<Blockable> state1 = new BillingState<Blockable>(objectId, 1, new BigDecimal("100.00"), now.minusDays(10),
                                                                           DateTimeZone.UTC, unpaidInvoiceId, PaymentResponse.INSUFFICIENT_FUNDS,
                                                                           new Tag[]{new DefaultControlTag(ControlTagType.OVERDUE_ENFORCEMENT_OFF, objectType, objectId, clock.getUTCNow())});

        final BillingState<Blockable> state2 = new BillingState<Blockable>(objectId, 1, new BigDecimal("200.00"), now.minusDays(20),
                                                                           DateTimeZone.UTC, unpaidInvoiceId,
                                                                           PaymentResponse.DO_NOT_HONOR,
                                                                           new Tag[]{new DefaultControlTag(ControlTagType.OVERDUE_ENFORCEMENT_OFF, objectType, objectId, clock.getUTCNow()),
                                                                                     new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF, objectType, objectId, clock.getUTCNow()),
                                                                                     new DescriptiveTag(UUID.randomUUID(), objectType, objectId, clock.getUTCNow())});

        Assert.assertTrue(!c.evaluate(state0, now));
        Assert.assertTrue(c.evaluate(state1, now));
        Assert.assertTrue(c.evaluate(state2, now));
    }
}
