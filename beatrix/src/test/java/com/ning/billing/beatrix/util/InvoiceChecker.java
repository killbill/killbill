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


import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceUserApi;

public class InvoiceChecker {

    private final static Logger log = LoggerFactory.getLogger(InvoiceChecker.class);

    private final InvoiceUserApi invoiceUserApi;

    @Inject
    public InvoiceChecker(final InvoiceUserApi invoiceUserApi) {
       this.invoiceUserApi = invoiceUserApi;
    }

    public void checkInvoice(final UUID invoiceId, final List<ExpectedItemCheck> expected) {
        final Invoice invoice =invoiceUserApi.getInvoice(invoiceId);
        Assert.assertNotNull(invoice);

        final List<InvoiceItem> actual = invoice.getInvoiceItems();
        Assert.assertEquals(expected.size(), actual.size());
        for (ExpectedItemCheck cur : expected) {
            boolean found = false;
            for (InvoiceItem in : actual) {
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
                Assert.fail(String.format("Failed to find invoice item type = %s and startDate = %s, amount = %s, endDate = %s",
                        cur.getType(), cur.getStartDate(), cur.getAmount(), cur.getEndDate()));
            }
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
