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

package com.ning.billing.junction.blocking;

import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.block.BlockingChecker;

public class MockBlockingChecker implements BlockingChecker {

    @Override
    public void checkBlockedChange(final Blockable blockable) throws BlockingApiException {
        // Intentionally blank

    }

    @Override
    public void checkBlockedEntitlement(final Blockable blockable) throws BlockingApiException {
        // Intentionally blank
    }

    @Override
    public void checkBlockedBilling(final Blockable blockable) throws BlockingApiException {
        // Intentionally blank
    }

    @Override
    public void checkBlockedChange(final UUID bundleId, final Type type) throws BlockingApiException {
        // Intentionally blank
    }

    @Override
    public void checkBlockedEntitlement(final UUID bundleId, final Type type) throws BlockingApiException {
        // Intentionally blank
    }

    @Override
    public void checkBlockedBilling(final UUID bundleId, final Type type) throws BlockingApiException {
        // Intentionally blank
    }

}
