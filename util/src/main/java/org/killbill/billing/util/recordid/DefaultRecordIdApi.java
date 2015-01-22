/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.recordid;

import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultRecordIdApi implements RecordIdApi {

    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultRecordIdApi(final InternalCallContextFactory internalCallContextFactory) {
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Long getRecordId(final UUID objectId, final ObjectType objectType, final TenantContext tenantContext) {
        return internalCallContextFactory.getRecordIdFromObject(objectId, objectType, tenantContext);
    }
}
