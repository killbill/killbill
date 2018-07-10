/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.callcontext;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.clock.DefaultClock;

public class TestCallContext implements CallContext {

    private final String userName;
    private final DateTime updatedDate;
    private final DateTime createdDate;
    private final UUID userToken;
    private final UUID accountId;
    private final UUID tenantId;

    public TestCallContext(final String userName) {
        this(userName, new DefaultClock().getUTCNow(), new DefaultClock().getUTCNow());
    }

    public TestCallContext(final CallContext context, final DateTime utcNow) {
        this.userName = context.getUserName();
        this.createdDate = utcNow;
        this.updatedDate = utcNow;
        this.userToken = context.getUserToken();
        this.accountId = context.getAccountId();
        this.tenantId = context.getTenantId();
    }

    public TestCallContext(final String userName, final DateTime createdDate, final DateTime updatedDate) {
        this.userName = userName;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.userToken = UUID.randomUUID();
        this.accountId = UUID.randomUUID();
        this.tenantId = UUID.randomUUID();
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
    public String getReasonCode() {
        return null;
    }

    @Override
    public String getComments() {
        return null;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
    }
}
