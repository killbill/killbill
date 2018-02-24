/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.beatrix.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

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
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, false, context);
        //Assert.assertEquals(invoices.size(), invoiceOrderingNumber);
        final Invoice invoice = invoices.get(invoiceOrderingNumber - 1);
        checkInvoice(invoice.getId(), context, expected);
        return invoice;
    }

    public void checkInvoice(final UUID invoiceId, final CallContext context, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        final Invoice invoice = invoiceUserApi.getInvoice(invoiceId, context);
        Assert.assertNotNull(invoice);
        checkInvoice(invoice, context, expected);
    }

    public void checkInvoiceNoAudits(final Invoice invoice, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        final List<InvoiceItem> actual = invoice.getInvoiceItems();
        Assert.assertEquals(actual.size(), expected.size(), String.format("Expected items: %s, actual items: %s", expected, actual));
        for (final ExpectedInvoiceItemCheck cur : expected) {
            boolean found = false;

            // First try to find exact match; this is necessary because the for loop below might encounter a similar item -- for instance
            // same type, same dates, but different amount and choke.
            final InvoiceItem foundItem = Iterables.tryFind(actual, new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem input) {
                    if (input.getInvoiceItemType() != cur.getType() || (cur.shouldCheckDates() && input.getStartDate().compareTo(cur.getStartDate()) != 0)) {
                        return false;
                    }
                    if (input.getAmount().compareTo(cur.getAmount()) != 0) {
                        return false;
                    }

                    if (!cur.shouldCheckDates() ||
                        (cur.getEndDate() == null && input.getEndDate() == null) ||
                        (cur.getEndDate() != null && input.getEndDate() != null && cur.getEndDate().compareTo(input.getEndDate()) == 0)) {
                        return true;
                    }
                    return false;
                }
            }).orNull();
            if (foundItem != null) {
                continue;
            }

            // If we could not find it, we still loop again, so that error message helps to debug when there is a 'similar' item.
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
                final StringBuilder debugBuilder = new StringBuilder();
                debugBuilder.append(String.format("Invoice id=[%s], targetDate=[%s]", invoice.getId(), invoice.getTargetDate()));
                for (final InvoiceItem actualInvoiceItem : actual) {
                    debugBuilder.append(String.format("\n    type=[%s] startDate=[%s] endDate=[%s] amount=[%s]", actualInvoiceItem.getInvoiceItemType(), actualInvoiceItem.getStartDate(), actualInvoiceItem.getEndDate(), actualInvoiceItem.getAmount()));
                }

                final String failureMessage = String.format("Failed to find invoice item type = %s and startDate = %s, amount = %s, endDate = %s for invoice id %s\n%s",
                                                            cur.getType(), cur.getStartDate(), cur.getAmount(), cur.getEndDate(), invoice.getId(), debugBuilder.toString());
                Assert.fail(failureMessage);
            }
        }

    }

    public void checkInvoice(final Invoice invoice, final CallContext context, final List<ExpectedInvoiceItemCheck> expected) throws InvoiceApiException {
        checkInvoiceNoAudits(invoice, expected);
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
                final String msg = String.format("Checking CTD for entitlement %s : expectedLocalCTD = %s, got %s",
                                                 entitlementId, expectedLocalCTD, subscription.getChargedThroughDate().toLocalDate());
                assertTrue(expectedLocalCTD.compareTo(subscription.getChargedThroughDate().toLocalDate()) == 0, msg);
            }
        } catch (final EntitlementApiException e) {
            fail("Failed to retrieve entitlement for " + entitlementId);
        }
    }

    public static class ExpectedInvoiceItemCheck {

        private final boolean checkDates;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final InvoiceItemType type;
        private final BigDecimal amount;

        public ExpectedInvoiceItemCheck(final InvoiceItemType type, final BigDecimal amount, final boolean checkDates) {
            this.checkDates = checkDates;
            this.type = type;
            this.startDate = null;
            this.endDate = null;
            this.amount = amount;
        }

        public ExpectedInvoiceItemCheck(final InvoiceItemType type, final BigDecimal amount) {
            this(type, amount, false);
        }

        public ExpectedInvoiceItemCheck(final LocalDate startDate, final LocalDate endDate,
                                        final InvoiceItemType type, final BigDecimal amount, final boolean checkDates) {
            this.checkDates = checkDates;
            this.startDate = startDate;
            this.endDate = endDate;
            this.type = type;
            this.amount = amount;
        }

        public ExpectedInvoiceItemCheck(final LocalDate startDate, final LocalDate endDate,
                                        final InvoiceItemType type, final BigDecimal amount) {
            this(startDate, endDate, type, amount, true);
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
            return amount;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ExpectedInvoiceItemCheck{");
            sb.append("checkDates=").append(checkDates);
            sb.append(", startDate=").append(startDate);
            sb.append(", endDate=").append(endDate);
            sb.append(", type=").append(type);
            sb.append(", amount=").append(amount);
            sb.append('}');
            return sb.toString();
        }
    }
}
