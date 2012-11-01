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

/**
 * The various <code>BillingAlignement<code/> supported in Killbill.
 * <p>
 * The <code>Catalog</code> will define the billing alignement for each <code>Plan</code>
 *
 * @see com.ning.billing.catalog.api.Plan
 * @see com.ning.billing.catalog.api.PlanPhase
 * @see com.ning.billing.catalog.api.ProductCategory
 */
public enum BillingAlignment {
    /**
     * All {@code Subscription}s whose {@code Plan} has been configured with this alignment will
     * be invoiced using the {@code Account} billCycleDay.
     */
    ACCOUNT,

    /**
     * All {@code Subscription}s whose {@code Plan} has been configured with this alignment will
     * be invoiced using the startDate of the first billable {@code PlanPhase} for the {@code ProductCategory.BASE}
     * {@code Plan}.
     */
    BUNDLE,

    /**
     * All {@code Subscription}s whose {@code Plan} has been configured with this alignment will
     * be invoiced using the startDate of the first billable {@code PlanPhase} for the
     * {@code Subscription}.
     */
    SUBSCRIPTION
}
