/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.cache;

import java.util.Arrays;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.skife.jdbi.v2.Handle;

public class CacheLoaderArgument {

    private final ObjectType objectType;
    private final Object[] args;
    private final InternalTenantContext internalTenantContext;
    private final Handle handle;

    public CacheLoaderArgument(final ObjectType objectType) {
        this(objectType, new Object[]{}, null);
    }

    public CacheLoaderArgument(final ObjectType objectType, final Object[] args, @Nullable final InternalTenantContext internalTenantContext) {
        this(objectType, args, internalTenantContext, null);
    }

    public CacheLoaderArgument(final ObjectType objectType, final Object[] args, @Nullable final InternalTenantContext internalTenantContext, @Nullable final Handle handle) {
        this.objectType = objectType;
        this.args = args;
        this.internalTenantContext = internalTenantContext;
        this.handle = handle;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public Object[] getArgs() {
        return args;
    }

    public InternalTenantContext getInternalTenantContext() {
        return internalTenantContext;
    }

    public Handle getHandle() {
        return handle;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CacheLoaderArgument{");
        sb.append("objectType=").append(objectType);
        sb.append(", args=").append(Arrays.toString(args));
        sb.append(", internalTenantContext=").append(internalTenantContext);
        sb.append('}');
        return sb.toString();
    }
}
