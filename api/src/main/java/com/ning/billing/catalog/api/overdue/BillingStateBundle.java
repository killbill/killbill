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

package com.ning.billing.catalog.api.overdue;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.util.tag.Tag;

public class BillingStateBundle extends BillingState {
	private final BillingState accountState;
    private final Product basePlanProduct;
    private final BillingPeriod basePlanBillingPeriod;
    private final PriceList basePlanPriceList;
    
	public BillingStateBundle(UUID id, BillingState accountState, int numberOfUnpaidInvoices, BigDecimal unpaidInvoiceBalance,
			DateTime dateOfEarliestUnpaidInvoice,
			PaymentResponse responseForLastFailedPayment,
			Tag[] tags, 
			Product basePlanProduct,
			BillingPeriod basePlanBillingPeriod, 
			PriceList basePlanPriceList) {
		super(id, numberOfUnpaidInvoices, unpaidInvoiceBalance, 
				dateOfEarliestUnpaidInvoice, responseForLastFailedPayment, tags);
		this.accountState = accountState;
		this.basePlanProduct = basePlanProduct;
		this.basePlanBillingPeriod = basePlanBillingPeriod;
		this.basePlanPriceList = basePlanPriceList;
	}
	
	public BillingState getAccountState() {
		return accountState;
	}
	
	public Product getBasePlanProduct() {
		return basePlanProduct;
	}
	
	public BillingPeriod getBasePlanBillingPeriod() {
		return basePlanBillingPeriod;
	}
	
	public PriceList getBasePlanPriceList() {
		return basePlanPriceList;
	}
}
