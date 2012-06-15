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

package com.ning.billing.util.callcontext;

import javax.annotation.Nullable;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.util.clock.Clock;

public class DefaultCallContextFactory implements CallContextFactory {
    private final Clock clock;

    @Inject
    public DefaultCallContextFactory(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public CallContext createCallContext(final String userName, final CallOrigin callOrigin, final UserType userType,
                                         @Nullable final UUID userToken) {
        return new DefaultCallContext(userName, callOrigin, userType, userToken, clock);
    }

    @Override
    public CallContext createCallContext(final String userName, final CallOrigin callOrigin, final UserType userType,
                                         final String reasonCode, final String comment, final UUID userToken) {
        return new DefaultCallContext(userName, callOrigin, userType, reasonCode, comment, userToken, clock);
    }

    @Override
    public CallContext createCallContext(final String userName, final CallOrigin callOrigin, final UserType userType) {
        return createCallContext(userName, callOrigin, userType, null);
    }

    @Override
    public CallContext createMigrationCallContext(final String userName, final CallOrigin callOrigin, final UserType userType, final DateTime createdDate, final DateTime updatedDate) {
        return new MigrationCallContext(userName, callOrigin, userType, createdDate, updatedDate);
    }

    @Override
    public CallContext toMigrationCallContext(final CallContext callContext, final DateTime createdDate, final DateTime updatedDate) {
        return new MigrationCallContext(callContext, createdDate, updatedDate);
    }
}
