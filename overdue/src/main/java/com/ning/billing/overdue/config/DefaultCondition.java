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

package com.ning.billing.overdue.config;

import java.math.BigDecimal;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

@XmlAccessorType(XmlAccessType.NONE)

public class DefaultCondition<T extends Blockable> extends ValidatingConfig<OverdueConfig> implements Condition<T> {
	@XmlElement(required=false, name="numberOfUnpaidInvoicesEqualsOrExceeds")
	private Integer numberOfUnpaidInvoicesEqualsOrExceeds;

	@XmlElement(required=false, name="totalUnpaidInvoiceBalanceEqualsOrExceeds")
	private BigDecimal totalUnpaidInvoiceBalanceEqualsOrExceeds;

	@XmlElement(required=false, name="timeSinceEarliestUnpaidInvoiceEqualsOrExceeds")
	private DefaultDuration timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;

	@XmlElementWrapper(required=false, name="responseForLastFailedPaymentIn")
	@XmlElement(required=false, name="response")
	private PaymentResponse[] responseForLastFailedPayment;

	@XmlElement(required=false, name="controlTag")
	private ControlTagType controlTag;
	
	/* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.Condition#evaluate(com.ning.billing.catalog.api.overdue.BillingState, org.joda.time.DateTime)
     */
	@Override
    public boolean evaluate(BillingState<T> state, DateTime now) {
	    DateTime unpaidInvoiceTriggerDate = null;
	    if( timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null && state.getDateOfEarliestUnpaidInvoice() != null) {  // no date => no unpaid invoices
	        unpaidInvoiceTriggerDate = state.getDateOfEarliestUnpaidInvoice().plus(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.toJodaPeriod());
	    }
        
		return 
				(numberOfUnpaidInvoicesEqualsOrExceeds == null || state.getNumberOfUnpaidInvoices() >= numberOfUnpaidInvoicesEqualsOrExceeds.intValue() ) &&
				(totalUnpaidInvoiceBalanceEqualsOrExceeds == null || totalUnpaidInvoiceBalanceEqualsOrExceeds.compareTo(state.getBalanceOfUnpaidInvoices()) <= 0) &&
				(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds == null ||
				    (unpaidInvoiceTriggerDate != null && !unpaidInvoiceTriggerDate.isAfter(now))) &&
				(responseForLastFailedPayment == null || responseIsIn(state.getResponseForLastFailedPayment(), responseForLastFailedPayment)) &&
				(controlTag == null || isTagIn(controlTag, state.getTags()));
	}
	
	private boolean responseIsIn(PaymentResponse actualResponse,
			PaymentResponse[] responseForLastFailedPayment) {
		for(PaymentResponse response: responseForLastFailedPayment) {
			if(response.equals(actualResponse)) return true;
		}
		return false;
	}

	private boolean isTagIn(ControlTagType tag, Tag[] tags) {
		for(Tag t : tags) {
			if (t.getTagDefinitionName().equals(tag.toString())) return true;
		}
		return false;
	}

	@Override
	public ValidationErrors validate(OverdueConfig root,
			ValidationErrors errors) {
		return errors;
	}

	@Override
	public void initialize(OverdueConfig root, URI uri) {
	}

    public Duration getTimeOffset() {
        if (timeSinceEarliestUnpaidInvoiceEqualsOrExceeds != null) {
            return timeSinceEarliestUnpaidInvoiceEqualsOrExceeds;
        } else { 
            return new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(0); // zero time
        }
        
    }
}
