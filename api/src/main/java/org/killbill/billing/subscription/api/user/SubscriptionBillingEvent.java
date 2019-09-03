/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

public interface SubscriptionBillingEvent extends Comparable<SubscriptionBillingEvent> {

    Plan getPlan();

    PlanPhase getPlanPhase();

    DateTime getEffectiveDate();

    Long getTotalOrdering();

    SubscriptionBaseTransitionType getType();

    Integer getBcdLocal();

    DateTime getCatalogEffectiveDate();

}
