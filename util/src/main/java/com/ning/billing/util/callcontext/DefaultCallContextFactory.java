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

import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;

public class DefaultCallContextFactory implements CallContextFactory {
    private final Clock clock;

    @Inject
    public DefaultCallContextFactory(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CallContext createCallContext(String userName, CallOrigin callOrigin, UserType userType, UUID userToken) {
        return new DefaultCallContext(userName, callOrigin, userType, userToken, clock);
    }

    @Override
    public CallContext createCallContext(String userName, CallOrigin callOrigin, UserType userType) {
    	return createCallContext(userName, callOrigin, userType, null);
    }

    @Override
    public CallContext createMigrationCallContext(String userName, CallOrigin callOrigin, UserType userType, DateTime createdDate, DateTime updatedDate) {
        return new MigrationCallContext(userName, callOrigin, userType, createdDate, updatedDate);
    }

    @Override
    public CallContext toMigrationCallContext(CallContext callContext, DateTime createdDate, DateTime updatedDate) {
        return new MigrationCallContext(callContext, createdDate, updatedDate);
    }
}
