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

package com.ning.billing.overdue.config.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.util.tag.Tag;

public class BillingState<T extends Blockable> {
    private final UUID objectId;
    private final int numberOfUnpaidInvoices;
    private final BigDecimal balanceOfUnpaidInvoices;
    private final LocalDate dateOfEarliestUnpaidInvoice;
    private final DateTimeZone accountTimeZone;
    private final UUID idOfEarliestUnpaidInvoice;
    private final PaymentResponse responseForLastFailedPayment;
    private final Tag[] tags;

    public BillingState(final UUID id,
                        final int numberOfUnpaidInvoices,
                        final BigDecimal balanceOfUnpaidInvoices,
                        final LocalDate dateOfEarliestUnpaidInvoice,
                        final DateTimeZone accountTimeZone,
                        final UUID idOfEarliestUnpaidInvoice,
                        final PaymentResponse responseForLastFailedPayment,
                        final Tag[] tags) {
        this.objectId = id;
        this.numberOfUnpaidInvoices = numberOfUnpaidInvoices;
        this.balanceOfUnpaidInvoices = balanceOfUnpaidInvoices;
        this.dateOfEarliestUnpaidInvoice = dateOfEarliestUnpaidInvoice;
        this.accountTimeZone = accountTimeZone;
        this.idOfEarliestUnpaidInvoice = idOfEarliestUnpaidInvoice;
        this.responseForLastFailedPayment = responseForLastFailedPayment;
        this.tags = tags;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public int getNumberOfUnpaidInvoices() {
        return numberOfUnpaidInvoices;
    }

    public BigDecimal getBalanceOfUnpaidInvoices() {
        return balanceOfUnpaidInvoices;
    }

    public LocalDate getDateOfEarliestUnpaidInvoice() {
        return dateOfEarliestUnpaidInvoice;
    }

    public UUID getIdOfEarliestUnpaidInvoice() {
        return idOfEarliestUnpaidInvoice;
    }

    public PaymentResponse getResponseForLastFailedPayment() {
        return responseForLastFailedPayment;
    }

    public Tag[] getTags() {
        return tags;
    }

    public DateTimeZone getAccountTimeZone() {
        return accountTimeZone;
    }
}
