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

package com.ning.billing.catalog.overdue;

import java.math.BigDecimal;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.joda.time.DateTime;

import com.ning.billing.account.api.ControlTagType;
import com.ning.billing.catalog.DefaultDuration;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.catalog.api.overdue.BillingState;
import com.ning.billing.catalog.api.overdue.PaymentResponse;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;
import com.ning.billing.util.tag.Tag;

@XmlAccessorType(XmlAccessType.NONE)
public class Condition extends ValidatingConfig<StandaloneCatalog> {
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
	
	public boolean evaluate(BillingState state, DateTime now) {
		return 
				(numberOfUnpaidInvoicesEqualsOrExceeds == null || state.getNumberOfUnpaidInvoices() >= numberOfUnpaidInvoicesEqualsOrExceeds.intValue() ) &&
				(totalUnpaidInvoiceBalanceEqualsOrExceeds == null || totalUnpaidInvoiceBalanceEqualsOrExceeds.compareTo(state.getBalanceOfUnpaidInvoices()) <= 0) &&
				(timeSinceEarliestUnpaidInvoiceEqualsOrExceeds == null || !timeSinceEarliestUnpaidInvoiceEqualsOrExceeds.addToDateTime(state.getDateOfEarliestUnpaidInvoice()).isAfter(now)) &&
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
	public ValidationErrors validate(StandaloneCatalog root,
			ValidationErrors errors) {
		return errors;
	}

	@Override
	public void initialize(StandaloneCatalog root, URI uri) {
	}
}
