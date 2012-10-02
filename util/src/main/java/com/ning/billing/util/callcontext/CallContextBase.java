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

import javax.annotation.Nullable;

public abstract class CallContextBase implements CallContext {

    protected final UUID tenantId;
    protected final UUID userToken;
    protected final String userName;
    protected final CallOrigin callOrigin;
    protected final UserType userType;
    protected final String reasonCode;
    protected final String comment;

    public CallContextBase(@Nullable final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType) {
        this(tenantId, userName, callOrigin, userType, null);
    }

    public CallContextBase(@Nullable final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType, final UUID userToken) {
        this(tenantId, userName, callOrigin, userType, null, null, userToken);
    }

    public CallContextBase(@Nullable final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType,
                           final String reasonCode, final String comment, final UUID userToken) {
        this.tenantId = tenantId;
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.userToken = userToken;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
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
    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }
}
