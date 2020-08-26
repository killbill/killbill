/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.overdue.config.api;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.payment.api.PaymentResponse;
import org.killbill.billing.util.tag.Tag;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class BillingState {

    private final UUID objectId;
    private final int numberOfUnpaidInvoices;
    private final BigDecimal balanceOfUnpaidInvoices;
    private final LocalDate dateOfEarliestUnpaidInvoice;
    private final UUID idOfEarliestUnpaidInvoice;
    private final PaymentResponse responseForLastFailedPayment;
    private final Tag[] tags;

    public BillingState(final UUID id,
                        final int numberOfUnpaidInvoices,
                        final BigDecimal balanceOfUnpaidInvoices,
                        final LocalDate dateOfEarliestUnpaidInvoice,
                        final UUID idOfEarliestUnpaidInvoice,
                        final PaymentResponse responseForLastFailedPayment,
                        final Tag[] tags) {
        this.objectId = id;
        this.numberOfUnpaidInvoices = numberOfUnpaidInvoices;
        this.balanceOfUnpaidInvoices = balanceOfUnpaidInvoices;
        this.dateOfEarliestUnpaidInvoice = dateOfEarliestUnpaidInvoice;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BillingState{");
        sb.append("objectId=").append(objectId);
        sb.append(", numberOfUnpaidInvoices=").append(numberOfUnpaidInvoices);
        sb.append(", balanceOfUnpaidInvoices=").append(balanceOfUnpaidInvoices);
        sb.append(", dateOfEarliestUnpaidInvoice=").append(dateOfEarliestUnpaidInvoice);
        sb.append(", idOfEarliestUnpaidInvoice=").append(idOfEarliestUnpaidInvoice);
        sb.append(", responseForLastFailedPayment=").append(responseForLastFailedPayment);
        sb.append(", tags=").append(Arrays.toString(tags));
        sb.append('}');
        return sb.toString();
    }
}
