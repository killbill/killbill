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
package com.ning.billing.jaxrs.json;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class BundleJsonWithSubscriptions extends BundleJsonSimple {
    @JsonView(BundleTimelineViews.Timeline.class)
    private final List<SubscriptionJsonWithEvents> subscriptions;

    @JsonCreator
    public BundleJsonWithSubscriptions(@JsonProperty("bundleId") @Nullable final String bundleId,
                                       @JsonProperty("externalKey") @Nullable final String externalKey,
                                       @JsonProperty("subscriptions") @Nullable final List<SubscriptionJsonWithEvents> subscriptions) {
        super(bundleId, externalKey);
        this.subscriptions = subscriptions;
    }

    @JsonProperty("subscriptions")
    public List<SubscriptionJsonWithEvents> getSubscriptions() {
        return subscriptions;
    }

    public BundleJsonWithSubscriptions(@Nullable final UUID accountId, final BundleTimeline bundle) {
        super(bundle.getBundleId().toString(), bundle.getExternalKey());
        this.subscriptions = new LinkedList<SubscriptionJsonWithEvents>();
        for (final SubscriptionTimeline cur : bundle.getSubscriptions()) {
            this.subscriptions.add(new SubscriptionJsonWithEvents(bundle.getBundleId(), cur));
        }
    }

    public BundleJsonWithSubscriptions(final SubscriptionBundle bundle) {
        super(bundle.getId().toString(), bundle.getKey());
        this.subscriptions = null;
    }

    public BundleJsonWithSubscriptions() {
        this(null, null, null);
    }
}
