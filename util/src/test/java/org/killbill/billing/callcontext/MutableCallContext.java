/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.callcontext;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;

public class MutableCallContext implements CallContext {

    private final CallContext delegate;
    private DateTime createdDate;

    public MutableCallContext(final MutableInternalCallContext internalCallContext) {
        this.delegate = internalCallContext.toCallContext(null, null);
        this.createdDate = delegate.getCreatedDate();
    }

    @Override
    public UUID getUserToken() {
        return delegate.getUserToken();
    }

    @Override
    public String getUserName() {
        return delegate.getUserName();
    }

    @Override
    public CallOrigin getCallOrigin() {
        return delegate.getCallOrigin();
    }

    @Override
    public UserType getUserType() {
        return delegate.getUserType();
    }

    @Override
    public String getReasonCode() {
        return delegate.getReasonCode();
    }

    @Override
    public String getComments() {
        return delegate.getComments();
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return delegate.getUpdatedDate();
    }

    @Override
    public UUID getAccountId() {
        return delegate.getAccountId();
    }

    @Override
    public UUID getTenantId() {
        return delegate.getTenantId();
    }
}
