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

package com.ning.billing.entitlement.block;

import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.util.callcontext.InternalTenantContext;

public interface BlockingChecker {

    public static final Object TYPE_SUBSCRIPTION = "Subscription";
    public static final Object TYPE_BUNDLE = "Bundle";
    public static final Object TYPE_ACCOUNT = "Account";

    public static final Object ACTION_CHANGE = "Change";
    public static final Object ACTION_ENTITLEMENT = "Entitlement";
    public static final Object ACTION_BILLING = "Billing";


    public interface BlockingAggregator {
        public boolean isBlockChange();
        public boolean isBlockEntitlement();
        public boolean isBlockBilling();
    }

    // Only throws if we can't find the blockable enties
    public BlockingAggregator getBlockedStatus(Blockable blockable, InternalTenantContext context) throws BlockingApiException;

    public void checkBlockedChange(Blockable blockable, InternalTenantContext context) throws BlockingApiException;

    public void checkBlockedEntitlement(Blockable blockable, InternalTenantContext context) throws BlockingApiException;

    public void checkBlockedBilling(Blockable blockable, InternalTenantContext context) throws BlockingApiException;
}
