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

package com.ning.billing.beatrix.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class InvoiceChecker {

    private static final Logger log = LoggerFactory.getLogger(InvoiceChecker.class);

    private final InvoiceUserApi invoiceUserApi;
    private final EntitlementUserApi entitlementApi;

    @Inject
    public InvoiceChecker(final InvoiceUserApi invoiceUserApi, final EntitlementUserApi entitlementApi) {
        this.invoiceUserApi = invoiceUserApi;
        this.entitlementApi = entitlementApi;
    }

    public void checkInvoice(final UUID accountId, final int invoiceOrderingNumber, final TenantContext context, final ExpectedItemCheck... expected) throws InvoiceApiException {
        checkInvoice(accountId, invoiceOrderingNumber, context, ImmutableList.<ExpectedItemCheck>copyOf(expected));
    }

    public void checkInvoice(final UUID accountId, final int invoiceOrderingNumber, final TenantContext context, final List<ExpectedItemCheck> expected) throws InvoiceApiException {
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, context);
        Assert.assertEquals(invoices.size(), invoiceOrderingNumber);
        final Invoice invoice = invoices.get(invoiceOrderingNumber - 1);
        checkInvoice(invoice.getId(), context, expected);
    }

    public void checkRepairedInvoice(final UUID accountId, final int invoiceNb, final TenantContext context, final ExpectedItemCheck... expected) throws InvoiceApiException {
        checkRepairedInvoice(accountId, invoiceNb, context, ImmutableList.<ExpectedItemCheck>copyOf(expected));
    }

    public void checkRepairedInvoice(final UUID accountId, final int invoiceNb, final TenantContext context, final List<ExpectedItemCheck> expected) throws InvoiceApiException {
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, context);
        Assert.assertTrue(invoices.size() > invoiceNb);
        final Invoice invoice = invoices.get(invoiceNb - 1);
        checkInvoice(invoice.getId(), context, expected);
    }

    public void checkInvoice(final UUID invoiceId, final TenantContext context, final List<ExpectedItemCheck> expected) throws InvoiceApiException {
        final Invoice invoice = invoiceUserApi.getInvoice(invoiceId, context);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> actual = invoice.getInvoiceItems();
        Assert.assertEquals(expected.size(), actual.size());
        for (final ExpectedItemCheck cur : expected) {
            boolean found = false;
            for (final InvoiceItem in : actual) {
                // Match first on type and start date
                if (in.getInvoiceItemType() != cur.getType() || (in.getStartDate().compareTo(cur.getStartDate()) != 0)) {
                    continue;
                }
                if (in.getAmount().compareTo(cur.getAmount()) != 0) {
                    log.info(String.format("Found item type = %s and startDate = %s but amount differ (actual = %s, expected = %s) ",
                                           cur.getType(), cur.getStartDate(), in.getAmount(), cur.getAmount()));
                    continue;
                }

                if ((cur.getEndDate() == null && in.getEndDate() == null) ||
                    (cur.getEndDate() != null && in.getEndDate() != null && cur.getEndDate().compareTo(in.getEndDate()) == 0)) {
                    found = true;
                    break;
                }
                log.info(String.format("Found item type = %s and startDate = %s, amount = %s but endDate differ (actual = %s, expected = %s) ",
                                       cur.getType(), cur.getStartDate(), in.getAmount(), in.getEndDate(), cur.getEndDate()));
            }
            if (!found) {
                Assert.fail(String.format("Failed to find invoice item type = %s and startDate = %s, amount = %s, endDate = %s for invoice id %s",
                                          cur.getType(), cur.getStartDate(), cur.getAmount(), cur.getEndDate(), invoice.getId()));
            }
        }
    }

    public void checkNullChargedThroughDate(final UUID subscriptionId, final TenantContext context) {
        checkChargedThroughDate(subscriptionId, null, context);
    }
// Checking CTD for subscription 2a8f1ca9-e463-4efb-bb4d-5314ec1a8e72 : expectedLocalCTD = 2012-05-01 => expectedCTD = 2012-05-01T00:03:42.000Z,
//
// got 2012-05-01T07:03:42.000Z expected:<true> but was:<false>

    public void checkChargedThroughDate(final UUID subscriptionId, final LocalDate expectedLocalCTD, final TenantContext context) {
        try {
            final Subscription subscription = entitlementApi.getSubscriptionFromId(subscriptionId, context);
            if (expectedLocalCTD == null) {
                assertNull(subscription.getChargedThroughDate());
            } else {
                final DateTime expectedCTD = expectedLocalCTD.toDateTime(new LocalTime(subscription.getStartDate().getMillis(), DateTimeZone.UTC), DateTimeZone.UTC);
                final String msg = String.format("Checking CTD for subscription %s : expectedLocalCTD = %s => expectedCTD = %s, got %s",
                                                 subscriptionId, expectedLocalCTD, expectedCTD, subscription.getChargedThroughDate());
                log.info(msg);
                assertNotNull(subscription.getChargedThroughDate());
                assertTrue(subscription.getChargedThroughDate().compareTo(expectedCTD) == 0, msg);
            }
        } catch (EntitlementUserApiException e) {
            fail("Failed to retrieve subscription for " + subscriptionId);
        }
    }

    public static class ExpectedItemCheck {

        private final LocalDate startDate;
        private final LocalDate endDate;
        private final InvoiceItemType type;
        private final BigDecimal Amount;

        public ExpectedItemCheck(final LocalDate startDate, final LocalDate endDate,
                                 final InvoiceItemType type, final BigDecimal amount) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.type = type;
            Amount = amount;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public InvoiceItemType getType() {
            return type;
        }

        public BigDecimal getAmount() {
            return Amount;
        }
    }

}
