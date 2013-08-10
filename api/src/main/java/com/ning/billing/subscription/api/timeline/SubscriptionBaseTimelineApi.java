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

package com.ning.billing.subscription.api.timeline;

import java.util.UUID;

import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface SubscriptionBaseTimelineApi {

    public BundleBaseTimeline getBundleTimeline(SubscriptionBaseBundle bundle, TenantContext context)
            throws SubscriptionBaseRepairException;

    public BundleBaseTimeline getBundleTimeline(UUID accountId, String bundleName, TenantContext context)
            throws SubscriptionBaseRepairException;

    public BundleBaseTimeline getBundleTimeline(UUID bundleId, TenantContext context)
            throws SubscriptionBaseRepairException;

    public BundleBaseTimeline repairBundle(BundleBaseTimeline input, boolean dryRun, CallContext context)
            throws SubscriptionBaseRepairException;
}
