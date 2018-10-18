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

package org.killbill.billing.callcontext;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;

public abstract class CallContextBase implements CallContext, Externalizable {

    protected UUID accountId;
    protected UUID tenantId;
    protected UUID userToken;
    protected String userName;
    protected CallOrigin callOrigin;
    protected UserType userType;
    protected String reasonCode;
    protected String comments;

    // For deserialization
    public CallContextBase() {
    }

    public CallContextBase(@Nullable final UUID accountId, @Nullable final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType, final UUID userToken) {
        this(accountId, tenantId, userName, callOrigin, userType, null, null, userToken);
    }

    public CallContextBase(@Nullable final UUID accountId, @Nullable final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType,
                           final String reasonCode, final String comment, final UUID userToken) {
        this.accountId = accountId;
        this.tenantId = tenantId;
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
        this.reasonCode = reasonCode;
        this.comments = comment;
        this.userToken = userToken;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
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
    public String getComments() {
        return comments;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeLong(accountId == null ? 0 : accountId.getMostSignificantBits());
        out.writeLong(accountId == null ? 0 : accountId.getLeastSignificantBits());
        out.writeLong(tenantId == null ? 0 : tenantId.getMostSignificantBits());
        out.writeLong(tenantId == null ? 0 : tenantId.getLeastSignificantBits());
        out.writeLong(userToken.getMostSignificantBits());
        out.writeLong(userToken.getLeastSignificantBits());
        out.writeUTF(userName);
        out.writeUTF(callOrigin.name());
        out.writeUTF(userType.name());
        out.writeUTF(reasonCode);
        out.writeUTF(comments);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.accountId = new UUID(in.readLong(), in.readLong());
        if (this.accountId.getMostSignificantBits() == 0) {
            this.accountId = null;
        }
        this.tenantId = new UUID(in.readLong(), in.readLong());
        if (this.tenantId.getMostSignificantBits() == 0) {
            this.tenantId = null;
        }
        this.userToken = new UUID(in.readLong(), in.readLong());
        this.userName = in.readUTF();
        this.callOrigin = CallOrigin.valueOf(in.readUTF());
        this.userType = UserType.valueOf(in.readUTF());
        this.reasonCode = in.readUTF();
        this.comments = in.readUTF();
    }
}
