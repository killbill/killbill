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

package com.ning.billing.util.entity;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;

public class DefaultCallContext implements CallContext {
    private final Clock clock;
    private final String userName;
    private final CallOrigin callOrigin;
    private final UserType userType;

    public DefaultCallContext(Clock clock, String userName, CallOrigin callOrigin, UserType userType) {
        this.clock = clock;
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    @Override
    public UserType getUserType() {
        return userType;
    }

    @Override
    public DateTime getUTCNow() {
        return clock.getUTCNow();
    }
}
