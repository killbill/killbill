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

public interface ICatalog {

	public abstract IProduct[] getProducts();
	
	public abstract IPlan findPlan(String productName, BillingPeriod term, String priceList) throws CatalogApiException;

	public abstract IPlan findPlan(String name) throws CatalogApiException;

    public abstract IProduct findProduct(String name) throws CatalogApiException;

    public abstract IPlanPhase findPhase(String name) throws CatalogApiException;

	
	public abstract Currency[] getSupportedCurrencies();

	public abstract IPlan[] getPlans();

	public abstract ActionPolicy planChangePolicy(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException;

	public abstract PlanChangeResult planChange(PlanPhaseSpecifier from,
			PlanSpecifier to) throws IllegalPlanChange, CatalogApiException;

    public abstract Date getEffectiveDate();

    public abstract ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase) throws CatalogApiException;

    public abstract void configureEffectiveDate(Date date);

    public abstract String getCalalogName();

    public abstract PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier) throws CatalogApiException;

    public abstract BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase) throws CatalogApiException;

    public abstract PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from,
			PlanSpecifier to) throws CatalogApiException;

	
}