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

package com.ning.billing.util.cache;

import javax.annotation.Nullable;

import com.ning.billing.ObjectType;
import com.ning.billing.util.callcontext.InternalTenantContext;

public class CacheLoaderArgument {

    private final ObjectType objectType;
    private final Object[] args;
    private final InternalTenantContext internalTenantContext;

    public CacheLoaderArgument(final ObjectType objectType) {
        this(objectType, new Object[]{}, null);
    }

    public CacheLoaderArgument(final ObjectType objectType, final Object[] args, @Nullable final InternalTenantContext internalTenantContext) {
        this.objectType = objectType;
        this.args = args;
        this.internalTenantContext = internalTenantContext;
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
}
