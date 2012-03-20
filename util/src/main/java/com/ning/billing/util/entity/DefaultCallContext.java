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

import com.ning.billing.ErrorCode;
import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;

public class DefaultCallContext extends CallContextBase {
    private final Clock clock;

    public DefaultCallContext(String userName, CallOrigin callOrigin, UserType userType, Clock clock) {
        super(userName, callOrigin, userType);
        this.clock = clock;
    }

    @Override
    public DateTime getCreatedDate() {
        return clock.getUTCNow();
    }

    @Override
    public DateTime getUpdatedDate() {
        return clock.getUTCNow();
    }
}