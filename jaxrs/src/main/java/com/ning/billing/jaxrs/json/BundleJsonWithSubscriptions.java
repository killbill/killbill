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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class BundleJsonWithSubscriptions extends BundleJsonSimple {

    @JsonView(BundleTimelineViews.Timeline.class)
    private final List<SubscriptionJsonWithEvents> subscriptions;

    @JsonCreator
    public BundleJsonWithSubscriptions(@JsonProperty("bundleId") String bundleId,
            @JsonProperty("externalKey") String externalKey,
            @JsonProperty("subscriptions") List<SubscriptionJsonWithEvents> subscriptions) {
        super(bundleId, externalKey);
        this.subscriptions = subscriptions;
    }

    @JsonProperty("subscriptions")
    public List<SubscriptionJsonWithEvents> getSubscriptions() {
        return subscriptions;
    }

    public BundleJsonWithSubscriptions(final UUID accountId, final BundleTimeline bundle) {
        super(bundle.getBundleId().toString(), bundle.getExternalKey());
        this.subscriptions = new LinkedList<SubscriptionJsonWithEvents>();
        for (SubscriptionTimeline cur : bundle.getSubscriptions()) {
            this.subscriptions.add(new SubscriptionJsonWithEvents(bundle.getBundleId(), cur)); 
        }
    }
    
    public BundleJsonWithSubscriptions(SubscriptionBundle bundle) {
        super(bundle.getId().toString(), bundle.getKey());        
        this.subscriptions = null;
    }
    
    public BundleJsonWithSubscriptions() {
        super(null, null);        
        this.subscriptions = null;
    }

}
