/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.Iterator;

import org.joda.time.DateTime;

/**
 * The interface {@code Plan}
 */
public interface Plan {

    /**
     * 
     * @return an array of {@code PlanPhase}
     */
    public PlanPhase[] getInitialPhases();

    /**
     *
     * @return the {@code Product} associated with that {@code Plan}
     */
    public Product getProduct();

    /**
     *
     * @return the name of the {@code Plan}
     */
    public String getName();

    /**
     *
     * @return whether the {@code Plan} has been retired
     */
    public boolean isRetired();

    /**
     *
     * @return an iterator through the {@code PlanPhase}
     */
    public Iterator<PlanPhase> getInitialPhaseIterator();

    /**
     *
     * @return the final {@code PlanPhase}
     */
    public PlanPhase getFinalPhase();

    /**
     *
     * @return the {@code BillingPeriod}
     */
    public BillingPeriod getBillingPeriod();

    /**
     *
     * @return the number of instance of subscriptions in a bundle with that {@code Plan}
     */
    public int getPlansAllowedInBundle();

    /**
     *
     * @return an array of {@code PlanPhase}
     */
    public PlanPhase[] getAllPhases();

    /**
     *
     * @return the date for which existing subscriptions become effective with that {@code Plan}
     */
    public Date getEffectiveDateForExistingSubscriptons();

    /**
     *
     * @param name  the name of the {@code PlanPhase}
     * @return      the {@code PlanPhase}
     *
     * @throws CatalogApiException if there is not such {@code PlanPhase}
     */
    public PlanPhase findPhase(String name) throws CatalogApiException;

    /**
     *
     * @param subscriptionStartDate the subscriptionStartDate
     * @param intialPhaseType       the type of the initial phase
     * @return                      the date at which we see the first recurring non zero charge
     */
    public DateTime dateOfFirstRecurringNonZeroCharge(DateTime subscriptionStartDate, PhaseType intialPhaseType);

}
