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

package com.ning.billing.catalog.api;

import com.ning.billing.ErrorCode;

public class IllegalPlanChange extends CatalogApiException {
	private static final long serialVersionUID = 1L;

	public IllegalPlanChange(Throwable cause, PlanPhaseSpecifier from,
			PlanSpecifier to) {
		super(cause, ErrorCode.CAT_ILLEGAL_CHANGE_REQUEST, from.getProductName(), from.getBillingPeriod(), from.getPriceListName(), to.getProductName(), to.getBillingPeriod(), to.getPriceListName());
	}

	public IllegalPlanChange(PlanPhaseSpecifier from,
			PlanSpecifier to) {
		super(ErrorCode.CAT_ILLEGAL_CHANGE_REQUEST, from.getProductName(), from.getBillingPeriod(), from.getPriceListName(), to.getProductName(), to.getBillingPeriod(), to.getPriceListName());
	}


}

