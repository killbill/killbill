/*
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;

import org.killbill.billing.jaxrs.json.CatalogJson.DurationJson;
import org.killbill.billing.overdue.api.OverdueCondition;
import org.killbill.billing.overdue.config.DefaultDuration;
import org.killbill.billing.overdue.config.DefaultOverdueCondition;
import org.killbill.billing.payment.api.PaymentResponse;
import org.killbill.billing.util.tag.ControlTagType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="OverdueCondition")
public class OverdueConditionJson {

    private final DurationJson timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
    private final ControlTagType controlTagInclusion;
    private final ControlTagType controlTagExclusion;
    private final Integer numberOfUnpaidInvoicesEqualsOrExceeds;
    private final PaymentResponse[] responseForLastFailedPayment;
    private final BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds;


    @JsonCreator
    public OverdueConditionJson(@JsonProperty("timeSinceEarliestUnpaidInvoiceEqualsOrExceeds") final DurationJson timeSinceEarliestUnpaidInvoiceEqualsOrExceeds,
                                @JsonProperty("controlTagInclusion") final ControlTagType controlTagInclusion,
                                @JsonProperty("controlTagExclusion") final ControlTagType controlTagExclusion,
                                @JsonProperty("numberOfUnpaidInvoicesEqualsOrExceeds") final Integer numberOfUnpaidInvoicesEqualsOrExceeds,
                                @JsonProperty("responseForLastFailedPayment") final PaymentResponse[] responseForLastFailedPayment,
                                @JsonProperty("totalUnpaidInvoiceBalanceEqualsOrExceeds") final BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds) {
        this.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds = timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
        this.controlTagInclusion = controlTagInclusion;
        this.controlTagExclusion = controlTagExclusion;
        this.numberOfUnpaidInvoicesEqualsOrExceeds = numberOfUnpaidInvoicesEqualsOrExceeds;
        this.responseForLastFailedPayment = responseForLastFailedPayment;
        this.totalUnpaidInvoiceBalanceEqualsOrExceeds = totalUnpaidInvoiceBalanceEqualsOrExceeds;
    }

    public OverdueConditionJson(final OverdueCondition overdueCondition) {
        this.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds = new DurationJson(overdueCondition.getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds());
        this.controlTagInclusion = overdueCondition.getInclusionControlTagType();
        this.controlTagExclusion = overdueCondition.getExclusionControlTagType();
        this.numberOfUnpaidInvoicesEqualsOrExceeds = overdueCondition.getNumberOfUnpaidInvoicesEqualsOrExceeds();
        this.responseForLastFailedPayment = overdueCondition.getResponseForLastFailedPaymentIn();
        this.totalUnpaidInvoiceBalanceEqualsOrExceeds = overdueCondition.getTotalUnpaidInvoiceBalanceEqualsOrExceeds();
    }

    public DurationJson getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() {
        return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
    }

    public ControlTagType getControlTagInclusion() {
        return controlTagInclusion;
    }

    public ControlTagType getControlTagExclusion() {
        return controlTagExclusion;
    }

    public Integer getNumberOfUnpaidInvoicesEqualsOrExceeds() {
        return numberOfUnpaidInvoicesEqualsOrExceeds;
    }

    public PaymentResponse[] getResponseForLastFailedPayment() {
        return responseForLastFailedPayment;
    }

    public BigDecimal getTotalUnpaidInvoiceBalanceEqualsOrExceeds() {
        return totalUnpaidInvoiceBalanceEqualsOrExceeds;
    }

    @Override
    public String toString() {
        return "OverdueConditionJson{" +
               "timeSinceEarliestUnpaidInvoiceEqualsOrExceeds=" + timeSinceEarliestUnpaidInvoiceEqualsOrExceeds +
               ", controlTagInclusion=" + controlTagInclusion +
               ", controlTagExclusion=" + controlTagExclusion +
               ", numberOfUnpaidInvoicesEqualsOrExceeds=" + numberOfUnpaidInvoicesEqualsOrExceeds +
               ", responseForLastFailedPayment=" + responseForLastFailedPayment +
               ", totalUnpaidInvoiceBalanceEqualsOrExceeds=" + totalUnpaidInvoiceBalanceEqualsOrExceeds +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverdueConditionJson)) {
            return false;
        }

        final OverdueConditionJson that = (OverdueConditionJson) o;

        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null ? !timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.equals(that.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds) : that.timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null) {
            return false;
        }
        if (controlTagInclusion != that.controlTagInclusion) {
            return false;
        }
        if (controlTagExclusion != that.controlTagExclusion) {
            return false;
        }
        if (!numberOfUnpaidInvoicesEqualsOrExceeds.equals(that.numberOfUnpaidInvoicesEqualsOrExceeds)) {
            return false;
        }
        if (responseForLastFailedPayment != that.responseForLastFailedPayment) {
            return false;
        }
        return totalUnpaidInvoiceBalanceEqualsOrExceeds.equals(that.totalUnpaidInvoiceBalanceEqualsOrExceeds);

    }

    @Override
    public int hashCode() {
        int result = timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null ? timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.hashCode() : 0;
        result = 31 * result + (controlTagInclusion != null ? controlTagInclusion.hashCode() : 0);
        result = 31 * result + (controlTagExclusion != null ? controlTagExclusion.hashCode() : 0);
        result = 31 * result + (numberOfUnpaidInvoicesEqualsOrExceeds != null ? numberOfUnpaidInvoicesEqualsOrExceeds.hashCode() : 0);
        result = 31 * result + (responseForLastFailedPayment != null ? responseForLastFailedPayment.hashCode() : 0);
        result = 31 * result + (totalUnpaidInvoiceBalanceEqualsOrExceeds != null ? totalUnpaidInvoiceBalanceEqualsOrExceeds.hashCode() : 0);
        return result;
    }

    public static DefaultOverdueCondition toOverdueCondition(final OverdueConditionJson input) {
        final DefaultOverdueCondition result = new DefaultOverdueCondition();
        if (input.getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds() != null) {
            result.setTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds(new DefaultDuration().setUnit(input.getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getUnit()).setNumber(input.getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getNumber()));
        }
        result.setControlTagInclusion(input.getControlTagInclusion());
        result.setControlTagExclusion(input.getControlTagExclusion());
        result.setNumberOfUnpaidInvoicesEqualsOrExceeds(input.getNumberOfUnpaidInvoicesEqualsOrExceeds());
        result.setResponseForLastFailedPayment(input.getResponseForLastFailedPayment());
        result.setTotalUnpaidInvoiceBalanceEqualsOrExceeds(input.getTotalUnpaidInvoiceBalanceEqualsOrExceeds());

        return result;
    }
}
