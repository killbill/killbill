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

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

public class BundleJson {

    @JsonView(BundleTimelineViews.Base.class)
    private final String bundleId;

    @JsonView(BundleTimelineViews.Base.class)
    private final String accountId;

    @JsonView(BundleTimelineViews.Base.class)
    private final String externalKey;

    @JsonView(BundleTimelineViews.Timeline.class)
    private final List<SubscriptionJson> subscriptions;

    @JsonCreator
    public BundleJson(@JsonProperty("bundle_id") String bundleId,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("external_key") String externalKey,
            @JsonProperty("subscriptions") List<SubscriptionJson> subscriptions) {
        super();
        this.bundleId = bundleId;
        this.accountId = accountId;
        this.externalKey = externalKey;
        this.subscriptions = subscriptions;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public List<SubscriptionJson> getSubscriptions() {
        return subscriptions;
    }
}
