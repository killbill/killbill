/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.block;

import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.block.DefaultBlockingChecker.DefaultBlockingAggregator;

public class StatelessBlockingChecker {

    public DefaultBlockingAggregator getBlockedState(final Iterable<BlockingState> accountEntitlementStates,
                                                     final Iterable<BlockingState> bundleEntitlementStates,
                                                     final Iterable<BlockingState> subscriptionEntitlementStates) {
        final DefaultBlockingAggregator result = getBlockedState(subscriptionEntitlementStates);
        result.or(getBlockedState(bundleEntitlementStates));
        result.or(getBlockedState(accountEntitlementStates));
        return result;
    }

    public DefaultBlockingAggregator getBlockedState(final Iterable<BlockingState> currentBlockableStatePerService) {
        final DefaultBlockingAggregator result = new DefaultBlockingAggregator();
        for (final BlockingState cur : currentBlockableStatePerService) {
            result.or(cur);
        }
        return result;
    }
}
