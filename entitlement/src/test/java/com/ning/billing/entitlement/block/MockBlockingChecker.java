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

import java.util.UUID;

import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingStateType;

public class MockBlockingChecker implements BlockingChecker {

    @Override
    public BlockingAggregator getBlockedStatus(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        return null;
    }

    @Override
    public BlockingAggregator getBlockedStatus(final UUID blockableId, final BlockingStateType type, final InternalTenantContext context) throws BlockingApiException {
        return null;
    }

    @Override
    public void checkBlockedChange(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
    }

    @Override
    public void checkBlockedEntitlement(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
    }

    @Override
    public void checkBlockedBilling(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
    }
}
