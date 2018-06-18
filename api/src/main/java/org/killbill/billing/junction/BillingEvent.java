/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.junction;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

public interface BillingEvent extends Comparable<BillingEvent> {

    /**
     * @return the billCycleDay in the account timezone as seen for that subscription at that time
     * <p/>
     * Note: The billCycleDay may come from the Account, or the bundle or the subscription itself
     */
    int getBillCycleDayLocal();

    /**
     * @return the subscription
     */
    SubscriptionBase getSubscription();

    /**
     * @return the date for when that event became effective
     */
    DateTime getEffectiveDate();

    /**
     * @return the plan phase
     */
    PlanPhase getPlanPhase();

    /**
     * @return the plan
     */
    Plan getPlan();

    /**
     * @return the billing period for the active phase
     */
    BillingPeriod getBillingPeriod();

    /**
     * @return the description of the billing event
     */
    String getDescription();

    /**
     * @return the fixed price for the phase
     */
    BigDecimal getFixedPrice();

    /**
     * @return the recurring price for the phase
     */
    BigDecimal getRecurringPrice(DateTime effectiveDate) throws CatalogApiException;

    /**
     * @return the currency for the account being invoiced
     */
    Currency getCurrency();

    /**
     * @return the transition type of the underlying subscription event that triggered this
     */
    SubscriptionBaseTransitionType getTransitionType();

    /**
     * @return a unique long indicating the ordering on which events got inserted on disk-- used for sorting only
     */
    Long getTotalOrdering();

    /**
     * @return the list of {@code Usage} section
     */
    List<Usage> getUsages();

    /**
     *
     * @return the catalog version (effective date) associated with this billing event.
     */
    public DateTime getCatalogEffectiveDate();
}