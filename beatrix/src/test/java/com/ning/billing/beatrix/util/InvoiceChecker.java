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

import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.util.callcontext.CallContext;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class InvoiceChecker {

    private static final Logger log = LoggerFactory.getLogger(InvoiceChecker.class);

    private final InvoiceUserApi invoiceUserApi;
    private final EntitlementApi entitlementApi;
    private final AuditChecker auditChecker;

    @Inject
    public InvoiceChecker(final InvoiceUserApi invoiceUserApi, final EntitlementApi entitlementApi, final AuditChecker auditChecker) {
        this.invoiceUserApi = invoiceUserApi;
        this.entitlementApi = entitlementApi;
        this.auditChecker = auditChecker;
    }

    public Invoice checkInvoice(final UUID accountId, final int invoiceOrderingNumber, final CallContext context, final ExpectedInvoiceItemCheck... expected) throws InvoiceApiException {
        return checkInvoice(accountId, invoiceOrderingNumber, context, ImmutableList.<ExpectedInvoiceItemCheck>copyOf(expected));
    }

    public Invoice checkInvoice(final UUID accountId, final int invoiceOrderingNumber, final CallContext context, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, context);
        //Assert.assertEquals(invoices.size(), invoiceOrderingNumber);
        final Invoice invoice = invoices.get(invoiceOrderingNumber - 1);
        checkInvoice(invoice.getId(), context, expected);
        return invoice;
    }

    public void checkRepairedInvoice(final UUID accountId, final int invoiceNb, final CallContext context, final ExpectedInvoiceItemCheck... expected) throws InvoiceApiException {
        checkRepairedInvoice(accountId, invoiceNb, context, ImmutableList.<ExpectedInvoiceItemCheck>copyOf(expected));
    }

    public void checkRepairedInvoice(final UUID accountId, final int invoiceNb, final CallContext context, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, context);
        Assert.assertTrue(invoices.size() > invoiceNb);
        final Invoice invoice = invoices.get(invoiceNb - 1);
        checkInvoice(invoice.getId(), context, expected);
    }

    public void checkInvoice(final UUID invoiceId, final CallContext context, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        final Invoice invoice = invoiceUserApi.getInvoice(invoiceId, context);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> actual = invoice.getInvoiceItems();
        Assert.assertEquals(actual.size(), expected.size());
        for (final ExpectedInvoiceItemCheck cur : expected) {
            boolean found = false;
            for (final InvoiceItem in : actual) {
                // Match first on type and start date
                if (in.getInvoiceItemType() != cur.getType() || (cur.shouldCheckDates() && in.getStartDate().compareTo(cur.getStartDate()) != 0)) {
                    continue;
                }
                if (in.getAmount().compareTo(cur.getAmount()) != 0) {
                    log.info(String.format("Found item type = %s and startDate = %s but amount differ (actual = %s, expected = %s) ",
                                           cur.getType(), cur.getStartDate(), in.getAmount(), cur.getAmount()));
                    continue;
                }

                if (!cur.shouldCheckDates() ||
                    (cur.getEndDate() == null && in.getEndDate() == null) ||
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
        auditChecker.checkInvoiceCreated(invoice, context);
    }

    public void checkNullChargedThroughDate(final UUID entitlementId, final CallContext context) {
        checkChargedThroughDate(entitlementId, null, context);
    }

    public void checkChargedThroughDate(final UUID entitlementId, final LocalDate expectedLocalCTD, final CallContext context) {
        try {
            final DefaultEntitlement entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(entitlementId, context);
            final SubscriptionBase subscription = entitlement.getSubscriptionBase();
            if (expectedLocalCTD == null) {
                assertNull(subscription.getChargedThroughDate());
            } else {
                assertTrue(expectedLocalCTD.compareTo(subscription.getChargedThroughDate().toLocalDate()) == 0);
                /*
                final DateTime expectedCTD = expectedLocalCTD.toDateTime(new LocalTime(subscription.getStartDate().getMillis(), DateTimeZone.UTC), DateTimeZone.UTC);
                final String msg = String.format("Checking CTD for entitlement %s : expectedLocalCTD = %s => expectedCTD = %s, got %s",
                                                 entitlementId, expectedLocalCTD, expectedCTD, subscription.getChargedThroughDate());
                log.info(msg);
                assertNotNull(subscription.getChargedThroughDate());
                assertTrue(subscription.getChargedThroughDate().compareTo(expectedCTD) == 0, msg);
                */
            }
        } catch (EntitlementApiException e) {
            fail("Failed to retrieve entitlement for " + entitlementId);
        }
    }

    public static class ExpectedInvoiceItemCheck {

        private final boolean checkDates;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final InvoiceItemType type;
        private final BigDecimal Amount;

        public ExpectedInvoiceItemCheck(final InvoiceItemType type, final BigDecimal amount) {
            this.checkDates = false;
            this.type = type;
            this.startDate = null;
            this.endDate = null;
            Amount = amount;
        }

        public ExpectedInvoiceItemCheck(final LocalDate startDate, final LocalDate endDate,
                                        final InvoiceItemType type, final BigDecimal amount) {
            this.checkDates = true;
            this.startDate = startDate;
            this.endDate = endDate;
            this.type = type;
            Amount = amount;
        }

        public boolean shouldCheckDates() {
            return checkDates;
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
