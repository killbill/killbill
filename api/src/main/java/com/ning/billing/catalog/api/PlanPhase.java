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



/**
 * The interface {@code PlanPhase} is used to express the various phases available on a given {@code Plan}
 * 
 * @see com.ning.billing.catalog.api.Plan
  */
public interface PlanPhase {

    /**
     *
     * @return the recurring {@code InternationalPrice} for that {@code Phase} if defined
     */
    public InternationalPrice getRecurringPrice();

    /**
     *
     * @return the fixed {@code InternationalPrice} for that {@code Phase} if defined
     */
    public InternationalPrice getFixedPrice();

    /**
     *
     * @return the {@code BillingPeriod}
     */
    public BillingPeriod getBillingPeriod();

    /**
     *
     * @return the unique name for that {@code Phase}
     */
    public String getName();

    /**
     *
     * @return the parent {@code Plan}
     */
    public Plan getPlan();

    /**
     *
     * @return the duration of that {@code PlanPhase}
     */
    public Duration getDuration();

    /**
     *
     * @return  the {@code PhaseType}
     */
    public PhaseType getPhaseType();

    /**
    *
    * @return  the {@code Limit}
    */
    public Limit[] getLimits();
    
    /**
    *
    * @return  compliance boolean
    */
    public boolean compliesWithLimits(String unit, double value);
}
