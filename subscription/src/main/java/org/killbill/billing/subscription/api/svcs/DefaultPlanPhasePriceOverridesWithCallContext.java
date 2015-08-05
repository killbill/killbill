/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.svcs;

import java.util.List;

import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.util.callcontext.CallContext;

public class DefaultPlanPhasePriceOverridesWithCallContext implements PlanPhasePriceOverridesWithCallContext {

    private final List<PlanPhasePriceOverride> overrides;
    private final CallContext callContext;

    public DefaultPlanPhasePriceOverridesWithCallContext(final List<PlanPhasePriceOverride> overrides, final CallContext callContext) {
        this.overrides = overrides;
        this.callContext = callContext;
    }

    @Override
    public List<PlanPhasePriceOverride> getOverrides() {
        return overrides;
    }

    @Override
    public CallContext getCallContext() {
        return callContext;
    }
}
