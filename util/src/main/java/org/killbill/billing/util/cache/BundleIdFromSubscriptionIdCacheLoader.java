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

package org.killbill.billing.util.cache;

import java.util.UUID;

import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;

@Singleton
public class BundleIdFromSubscriptionIdCacheLoader extends BaseCacheLoader<UUID, UUID> {

    @Override
    public CacheType getCacheType() {
        return CacheType.BUNDLE_ID_FROM_SUBSCRIPTION_ID;
    }

    @Override
    public UUID compute(final UUID key, final CacheLoaderArgument cacheLoaderArgument) {
        if (cacheLoaderArgument.getArgs() == null ||
            !(cacheLoaderArgument.getArgs()[0] instanceof LoaderCallback)) {
            throw new IllegalArgumentException("Missing LoaderCallback from the arguments ");
        }

        final LoaderCallback callback = (LoaderCallback) cacheLoaderArgument.getArgs()[0];
        return callback.loadBundleId(key, cacheLoaderArgument.getInternalTenantContext());
    }

    public interface LoaderCallback {

        UUID loadBundleId(final UUID subscriptionId, final InternalTenantContext context);
    }
}
