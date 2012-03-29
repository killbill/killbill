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

import org.joda.time.DateTime;

public interface Catalog {
	//
    // Simple getters
    //
    public abstract String getCatalogName();

    public abstract Currency[] getSupportedCurrencies(DateTime requestedDate) throws CatalogApiException;

	public abstract Product[] getProducts(DateTime requestedDate) throws CatalogApiException;
	
	public abstract Plan[] getPlans(DateTime requestedDate) throws CatalogApiException;

	
	//
	// Find a plan
	//

	public abstract Plan findPlan(String name, DateTime requestedDate) throws CatalogApiException;

	public abstract Plan findPlan(String productName, BillingPeriod term, String priceListName,
									DateTime requestedDate) throws CatalogApiException;
	
	public abstract Plan findPlan(String name, DateTime effectiveDate, DateTime subscriptionStartDate) throws CatalogApiException;

	public abstract Plan findPlan(String productName, BillingPeriod term, String priceListName,
									DateTime requestedDate, DateTime subscriptionStartDate) throws CatalogApiException;
	
	//
	// Find a product
	//
    public abstract Product findProduct(String name, DateTime requestedDate) throws CatalogApiException;

    //
    // Find a phase
    //  
    public abstract PlanPhase findPhase(String name, DateTime requestedDate, DateTime subscriptionStartDate) throws CatalogApiException;

    //
    // Find a priceList
    //  
    public abstract PriceList findPriceList(String name, DateTime requestedDate) throws CatalogApiException;

    //
    // Rules
    //
	public abstract ActionPolicy planChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

	public abstract PlanChangeResult planChange(PlanPhaseSpecifier from,
			PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

    public abstract ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase, DateTime requestedDate) throws CatalogApiException;

    public abstract PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier, DateTime requestedDate) throws CatalogApiException;

    public abstract BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase, DateTime requestedDate) throws CatalogApiException;

    public abstract PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

    public abstract boolean canCreatePlan(PlanSpecifier specifier, DateTime requestedDate) throws CatalogApiException;
		
}
