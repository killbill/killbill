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

import java.util.Date;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.overdue.BillingStateBundle;
import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.catalog.api.overdue.OverdueStateSet;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;


public interface StaticCatalog {
    //
    // Simple getters
    //
    public abstract String getCatalogName();
    
    public abstract Date getEffectiveDate() throws CatalogApiException;

    public abstract Currency[] getCurrentSupportedCurrencies() throws CatalogApiException;

	public abstract Product[] getCurrentProducts() throws CatalogApiException;
	
	public abstract Plan[] getCurrentPlans() throws CatalogApiException;
	
	//
	// Find a plan
	//
	public abstract Plan findCurrentPlan(String productName, BillingPeriod term, String priceList) throws CatalogApiException;

	public abstract Plan findCurrentPlan(String name) throws CatalogApiException;

	//
	// Find a product
	//
    public abstract Product findCurrentProduct(String name) throws CatalogApiException;

    //
    // Find a phase
    //
    public abstract PlanPhase findCurrentPhase(String name) throws CatalogApiException;
    
    //
    //  
    //
	public abstract ActionPolicy planChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException;

	public abstract PlanChangeResult planChange(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException;


    public abstract ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase) throws CatalogApiException;

    public abstract PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier) throws CatalogApiException;

    public abstract BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase) throws CatalogApiException;

    public abstract PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException;

    public abstract boolean canCreatePlan(PlanSpecifier specifier) throws CatalogApiException;

    public abstract OverdueStateSet<SubscriptionBundle> currentBundleOverdueStateSet() throws CatalogApiException;

}