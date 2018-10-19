/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)

public class DefaultOverdueCondition extends ValidatingConfig<DefaultOverdueConfig> implements ConditionEvaluation, OverdueCondition, Externalizable {

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
    public PaymentResponse[] getResponseForLastFailedPaymentIn() {
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultOverdueCondition that = (DefaultOverdueCondition) o;

        if (numberOfUnpaidInvoicesEqualsOrExceeds != null ? !numberOfUnpaidInvoicesEqualsOrExceeds.equals(that.numberOfUnpaidInvoicesEqualsOrExceeds) : that.numberOfUnpaidInvoicesEqualsOrExceeds != null) {
            return false;
        }
        if (totalUnpaidInvoiceBalanceEqualsOrExceeds != null ? !totalUnpaidInvoiceBalanceEqualsOrExceeds.equals(that.totalUnpaidInvoiceBalanceEqualsOrExceeds) : that.totalUnpaidInvoiceBalanceEqualsOrExceeds != null) {
            return false;
        }
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null ? !timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.equals(that.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds) : that.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null) {
            return false;
        }
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(responseForLastFailedPayment, that.responseForLastFailedPayment)) {
            return false;
        }
        if (controlTagInclusion != that.controlTagInclusion) {
            return false;
        }
        return controlTagExclusion == that.controlTagExclusion;
    }

    @Override
    public int hashCode() {
        int result = numberOfUnpaidInvoicesEqualsOrExceeds != null ? numberOfUnpaidInvoicesEqualsOrExceeds.hashCode() : 0;
        result = 31 * result + (totalUnpaidInvoiceBalanceEqualsOrExceeds != null ? totalUnpaidInvoiceBalanceEqualsOrExceeds.hashCode() : 0);
        result = 31 * result + (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null ? timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(responseForLastFailedPayment);
        result = 31 * result + (controlTagInclusion != null ? controlTagInclusion.hashCode() : 0);
        result = 31 * result + (controlTagExclusion != null ? controlTagExclusion.hashCode() : 0);
        return result;
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

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(numberOfUnpaidInvoicesEqualsOrExceeds != null);
        if (numberOfUnpaidInvoicesEqualsOrExceeds != null) {
            out.writeInt(numberOfUnpaidInvoicesEqualsOrExceeds);
        }
        out.writeObject(totalUnpaidInvoiceBalanceEqualsOrExceeds);
        out.writeObject(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds);
        out.writeObject(responseForLastFailedPayment);
        out.writeBoolean(controlTagInclusion != null);
        if (controlTagInclusion != null) {
            out.writeUTF(controlTagInclusion.name());
        }
        out.writeBoolean(controlTagExclusion != null);
        if (controlTagExclusion != null) {
            out.writeUTF(controlTagExclusion.name());
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.numberOfUnpaidInvoicesEqualsOrExceeds = in.readBoolean() ? in.readInt() : null;
        this.totalUnpaidInvoiceBalanceEqualsOrExceeds = (BigDecimal) in.readObject();
        this.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds = (DefaultDuration) in.readObject();
        this.responseForLastFailedPayment = (PaymentResponse[]) in.readObject();
        this.controlTagInclusion = in.readBoolean() ? ControlTagType.valueOf(in.readUTF()) : null;
        this.controlTagExclusion = in.readBoolean() ? ControlTagType.valueOf(in.readUTF()) : null;
    }
}
