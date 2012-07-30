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

import org.joda.time.DateTime;

import com.ning.billing.util.clock.Clock;

public class DefaultCallContext extends CallContextBase {

    private final DateTime createdDate;

    public DefaultCallContext(final String userName, final CallOrigin callOrigin, final UserType userType, final UUID userToken, final Clock clock) {
        super(userName, callOrigin, userType, userToken);
        this.createdDate = clock.getUTCNow();
    }

    public DefaultCallContext(final String userName, final CallOrigin callOrigin, final UserType userType,
                              final String reasonCode, final String comment,
                              final UUID userToken, final Clock clock) {
        super(userName, callOrigin, userType, reasonCode, comment, userToken);
        this.createdDate = clock.getUTCNow();
    }

    public DefaultCallContext(final String userName, final CallOrigin callOrigin, final UserType userType, final Clock clock) {
        this(userName, callOrigin, userType, null, clock);
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return createdDate;
    }
}
