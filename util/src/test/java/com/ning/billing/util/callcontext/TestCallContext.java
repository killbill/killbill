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

import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;

public class TestCallContext implements CallContext {
    private final String userName;
    private final DateTime updatedDate;
    private final DateTime createdDate;

    public TestCallContext(String userName) {
        this.userName = userName;
        DateTime now = new DefaultClock().getUTCNow();
        this.updatedDate = now;
        this.createdDate = now;
    }

    public TestCallContext(String userName, DateTime createdDate, DateTime updatedDate) {
        this.userName = userName;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public CallOrigin getCallOrigin() {
        return CallOrigin.TEST;
    }

    @Override
    public UserType getUserType() {
        return UserType.TEST;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }
}
