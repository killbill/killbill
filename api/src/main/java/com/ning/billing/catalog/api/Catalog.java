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

/**
 * The {@code Catalog} information for a specific tenant.
 *
 */
public interface Catalog {

    /**
     *
     * @return  the catalogName
     */
    public String getCatalogName();

    /**
     *
     * @param requestedDate specifies the state of the catalog for that date
     * @return              an array of available {@code Currency}s
     *
     * @throws CatalogApiException
     */
    public Currency[] getSupportedCurrencies(DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param requestedDate specifies the state of the catalog for that date
     * @return              an array of available {@code Product}s
     *
     * @throws CatalogApiException
     */
    public Product[] getProducts(DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param requestedDate specifies the state of the catalog for that date
     * @return              an array of available {@code Plan}s
     * @throws CatalogApiException
     */
    public Plan[] getPlans(DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param name          the unique name of the plan
     * @param requestedDate specifies the state of the catalog for that date
     * @return              the {@code Plan}
     *
     * @throws CatalogApiException if {@code Plan} does not exist
     */
    public Plan findPlan(String name, DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param productName   the unique name for the {@code Product}
     * @param billingPeriod the unique name for the {@code BillingPeriod}
     * @param priceListName the unique name for the {@code PriceList}
     * @param requestedDate specifies the state of the catalog for that date
     * @return              the {@code Plan}
     *
     * @throws CatalogApiException if {@code Plan} does not exist
     */
    public Plan findPlan(String productName, BillingPeriod billingPeriod, String priceListName,
                                  DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param name                  the unique name of the plan
     * @param requestedDate         specifies the state of the catalog for that date
     * @param subscriptionStartDate the startDate of the subscription
     * @return                      the {@code Plan}
     *
     * @throws CatalogApiException if {@code Plan} does not exist
     */
    public Plan findPlan(String name, DateTime requestedDate, DateTime subscriptionStartDate) throws CatalogApiException;

    /**
     *
     * @param productName   the unique name for the {@code Product}
     * @param billingPeriod the unique name for the {@code BillingPeriod}
     * @param priceListName the unique name for the {@code PriceList}
     * @param requestedDate specifies the state of the catalog for that date
     * @return              the {@code Plan}
     *
     * @throws CatalogApiException if {@code Plan} does not exist
     */
    public Plan findPlan(String productName, BillingPeriod billingPeriod, String priceListName,
                                  DateTime requestedDate, DateTime subscriptionStartDate) throws CatalogApiException;

    /**
     *
     * @param name          the unique name for the {@code Product}
     * @param requestedDate specifies the state of the catalog for that date
     * @return              the {@code Product}
     *
     * @throws CatalogApiException if {@code Product} does not exist
     */
    public Product findProduct(String name, DateTime requestedDate) throws CatalogApiException;

    /**
     *
     * @param name          the unique name for the {@code PlanPhase}
     * @param requestedDate specifies the state of the catalog for that date
     * @return              the {@code PlanPhase}
     *
     * @throws CatalogApiException if the {@code PlanPhase} does not exist
     */
    public PriceList findPriceList(String name, DateTime requestedDate) throws CatalogApiException;


    /**
     *
     * @param name                  the unique name for the {@code PlanPhase}
     * @param requestedDate         specifies the state of the catalog for that date
     * @param subscriptionStartDate the startDate of the subscription
     * @return                      the {@code PlanPhase}
     * @throws CatalogApiException if the {@code PlanPhase} does not exist
     */
    public PlanPhase findPhase(String name, DateTime requestedDate, DateTime subscriptionStartDate) throws CatalogApiException;


    // TODO : should they be private APIs

    public ActionPolicy planChangePolicy(PlanPhaseSpecifier from,
                                                  PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

    public PlanChangeResult planChange(PlanPhaseSpecifier from,
                                                PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

    public ActionPolicy planCancelPolicy(PlanPhaseSpecifier planPhase, DateTime requestedDate) throws CatalogApiException;

    public PlanAlignmentCreate planCreateAlignment(PlanSpecifier specifier, DateTime requestedDate) throws CatalogApiException;

    public BillingAlignment billingAlignment(PlanPhaseSpecifier planPhase, DateTime requestedDate) throws CatalogApiException;

    public PlanAlignmentChange planChangeAlignment(PlanPhaseSpecifier from,
                                                            PlanSpecifier to, DateTime requestedDate) throws CatalogApiException;

    public boolean canCreatePlan(PlanSpecifier specifier, DateTime requestedDate) throws CatalogApiException;

}
