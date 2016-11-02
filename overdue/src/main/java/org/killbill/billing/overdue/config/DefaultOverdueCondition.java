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

package org.killbill.billing.overdue.config;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.ConditionEvaluation;
import org.killbill.billing.overdue.api.OverdueCondition;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.payment.api.PaymentResponse;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;

@XmlAccessorType(XmlAccessType.NONE)

public class DefaultOverdueCondition extends ValidatingConfig<DefaultOverdueConfig> implements ConditionEvaluation, OverdueCondition {

    @XmlElement(required = false, name = "numberOfUnpaidInvoicesEqualsOrExceeds")
    private Integer numberOfUnpaidInvoicesEqualsOrExceeds;

    @XmlElement(required = false, name = "totalUnpaidInvoiceBalanceEqualsOrExceeds")
    private BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds;

    @XmlElement(required = false, name = "timeSinceEarliestUnpaidInvoiceEqualsOrExceeds")
    private DefaultDuration timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;

    @XmlElementWrapper(required = false, name = "responseForLastFailedPaymentIn")
    @XmlElement(required = false, name = "response")
    private PaymentResponse[] responseForLastFailedPayment;

    @XmlElement(required = false, name = "controlTagInclusion")
    private ControlTagType controlTagInclusion;

    @XmlElement(required = false, name = "controlTagExclusion")
    private ControlTagType controlTagExclusion;

    @Override
    public boolean evaluate(final BillingState state, final LocalDate date) {
        LocalDate unpaidInvoiceTriggerDate = null;
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null && state.getDateOfEarliestUnpaidInvoice() != null) {  // no date => no unpaid invoices
            unpaidInvoiceTriggerDate = state.getDateOfEarliestUnpaidInvoice().plus(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.toJodaPeriod());
        }

        return
                (numberOfUnpaidInvoicesEqualsOrExceeds == null || state.getNumberOfUnpaidInvoices() >= numberOfUnpaidInvoicesEqualsOrExceeds) &&
                (totalUnpaidInvoiceBalanceEqualsOrExceeds == null || totalUnpaidInvoiceBalanceEqualsOrExceeds.compareTo(state.getBalanceOfUnpaidInvoices()) <= 0) &&
                (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds == null ||
                 (unpaidInvoiceTriggerDate != null && !unpaidInvoiceTriggerDate.isAfter(date))) &&
                (responseForLastFailedPayment == null || responseIsIn(state.getResponseForLastFailedPayment(), responseForLastFailedPayment)) &&
                (controlTagInclusion == null || isTagIn(controlTagInclusion, state.getTags())) &&
                (controlTagExclusion == null || isTagNotIn(controlTagExclusion, state.getTags()));
    }

    private boolean responseIsIn(final PaymentResponse actualResponse,
                                 final PaymentResponse[] responseForLastFailedPayment) {
        for (final PaymentResponse response : responseForLastFailedPayment) {
            if (response.equals(actualResponse)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTagIn(final ControlTagType tagType, final Tag[] tags) {
        for (final Tag t : tags) {
            if (t.getTagDefinitionId().equals(tagType.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTagNotIn(final ControlTagType tagType, final Tag[] tags) {
        for (final Tag t : tags) {
            if (t.getTagDefinitionId().equals(tagType.getId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ValidationErrors validate(final DefaultOverdueConfig root,
                                     final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final DefaultOverdueConfig root, final URI uri) {
    }

    public Duration getTimeOffset() {
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null) {
            return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
        } else {
            return new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(0); // zero time
        }

    }

    @Override
    public Integer getNumberOfUnpaidInvoicesEqualsOrExceeds() {
        return numberOfUnpaidInvoicesEqualsOrExceeds;
    }

    @Override
    public BigDecimal getTotalUnpaidInvoiceBalanceEqualsOrExceeds() {
        return totalUnpaidInvoiceBalanceEqualsOrExceeds;
    }

    @Override
    public Duration getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() {
        return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
    }

    @Override
    public PaymentResponse [] getResponseForLastFailedPaymentIn() {
        return responseForLastFailedPayment;
    }

    @Override
    public ControlTagType getInclusionControlTagType() {
        return controlTagInclusion;
    }

    @Override
    public ControlTagType getExclusionControlTagType() {
        return controlTagExclusion;
    }

    public void setNumberOfUnpaidInvoicesEqualsOrExceeds(final Integer numberOfUnpaidInvoicesEqualsOrExceeds) {
        this.numberOfUnpaidInvoicesEqualsOrExceeds = numberOfUnpaidInvoicesEqualsOrExceeds;
    }

    public void setTotalUnpaidInvoiceBalanceEqualsOrExceeds(final BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds) {
        this.totalUnpaidInvoiceBalanceEqualsOrExceeds = totalUnpaidInvoiceBalanceEqualsOrExceeds;
    }

    public void setTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds(final DefaultDuration timeSinceEarliestUnpaidInvoiceEqualsOrExceeds) {
        this.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds = timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
    }

    public void setResponseForLastFailedPayment(final PaymentResponse[] responseForLastFailedPayment) {
        this.responseForLastFailedPayment = responseForLastFailedPayment;
    }

    public void setControlTagInclusion(final ControlTagType controlTagInclusion) {
        this.controlTagInclusion = controlTagInclusion;
    }

    public void setControlTagExclusion(final ControlTagType controlTagExclusion) {
        this.controlTagExclusion = controlTagExclusion;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultOverdueCondition{");
        sb.append("numberOfUnpaidInvoicesEqualsOrExceeds=").append(numberOfUnpaidInvoicesEqualsOrExceeds);
        sb.append(", totalUnpaidInvoiceBalanceEqualsOrExceeds=").append(totalUnpaidInvoiceBalanceEqualsOrExceeds);
        sb.append(", timeSinceEarliestUnpaidInvoiceEqualsOrExceeds=").append(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds);
        sb.append(", responseForLastFailedPayment=").append(Arrays.toString(responseForLastFailedPayment));
        sb.append(", controlTagInclusion=").append(controlTagInclusion);
        sb.append(", controlTagExclusion=").append(controlTagExclusion);
        sb.append('}');
        return sb.toString();
    }
}
