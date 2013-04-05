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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;

public class TestCallContext implements CallContext {

    @Override
    public UUID getUserToken() {
        return UUID.randomUUID();
    }

    @Override
    public String getUserName() {
        return UUID.randomUUID().toString();
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
    public String getReasonCode() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getComments() {
        return UUID.randomUUID().toString();
    }

    @Override
    public DateTime getCreatedDate() {
        return new DateTime(2010, 2, 4, 6, 8, 10, DateTimeZone.UTC);
    }

    @Override
    public DateTime getUpdatedDate() {
        return new DateTime(2011, 3, 5, 7, 9, 11, DateTimeZone.UTC);
    }

    @Override
    public UUID getTenantId() {
        return UUID.randomUUID();
    }
}
